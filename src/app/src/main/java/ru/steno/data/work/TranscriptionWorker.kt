package ru.steno.data.work

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.steno.*
import ru.steno.R
import ru.steno.data.audio.*
import ru.steno.data.engine.llm.Punctuation
import ru.steno.data.engine.sherpa.SherpaEngine
import ru.steno.data.local.*
import ru.steno.domain.*
import java.io.File

class Queue(private val context: Context, private val notes: Notes) {
    private val manager get() = WorkManager.getInstance(context)
    fun enqueue(id: Long) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>().setInputData(workDataOf("id" to id)).build()
        manager.enqueueUniqueWork("note-$id", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    suspend fun cancel(id: Long) {
        notes.cancel(id)
        manager.cancelUniqueWork("note-$id").await()
    }
    suspend fun retry(id: Long) { if (notes.retry(id) > 0) enqueue(id) }
    suspend fun recover() {
        notes.recover()
        notes.pending().forEach(::enqueue)
    }
}

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as StenoApp
        app.scope.launch { try { app.queue.cancel(intent.getLongExtra(EXTRA_NOTE_ID,-1)) } finally { pending.finish() } }
    }
}

class TranscriptionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx,params) {
    private val app = ctx.applicationContext as StenoApp
    private val id = params.inputData.getLong("id",-1)
    private val clock get() = SystemClock.elapsedRealtime()
    companion object { private val nativeLock = Mutex() }

    private fun foreground(note: Note, stage: ProcessingStage, fraction: Float): ForegroundInfo {
        val manager = app.getSystemService(NotificationManager::class.java)
        val channel = "transcription"
        manager.createNotificationChannel(NotificationChannel(channel,app.getString(R.string.processing),NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(app,id.toInt(),Intent(app,MainActivity::class.java).putExtra(EXTRA_NOTE_ID,id),flags)
        val cancel = PendingIntent.getBroadcast(app,id.toInt(),Intent(app,CancelReceiver::class.java).putExtra(EXTRA_NOTE_ID,id),flags)
        val percent = (fraction * 100).toInt()
        val notification = NotificationCompat.Builder(app,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(note.title).setContentText(app.resources.getStringArray(R.array.stages)[stage.ordinal] + " · $percent%")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setProgress(100,percent,false)
            .addAction(0,app.getString(R.string.cancel),cancel).build()
        return if (Build.VERSION.SDK_INT >= 35) ForegroundInfo(701,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            else if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(701,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(701,notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        app.ready.await()
        nativeLock.withLock {
            val note = app.notes.get(id) ?: return@withLock Result.success()
            if (note.status != ProcessingStatus.QUEUED) return@withLock Result.success()
            try { setForeground(foreground(note,ProcessingStage.PREPARING_MODELS,0f)) }
            catch (e: IllegalStateException) { return@withLock Result.retry() }
            if (app.notes.claim(id) == 0) return@withLock Result.success()
            val start = clock
            var lastPercent = -1
            var lastStage: ProcessingStage? = null
            suspend fun update(stage: ProcessingStage, fraction: Float) {
                currentCoroutineContext().ensureActive()
                if (lastStage == stage && lastPercent == (fraction * 100).toInt()) return
                if (app.notes.progress(id,stage,fraction) == 0) throw CancellationException()
                setForeground(foreground(note,stage,fraction))
                lastStage = stage
                lastPercent = (fraction * 100).toInt()
            }
            try {
                val settings = app.settings.flow.first()
                val engine = SherpaEngine(app.assets)
                var actualModel = AsrModelType.GIGA_AM
                if (settings.punctuationEnabled) {
                    update(ProcessingStage.PREPARING_MODELS,0f)
                    if (app.preparation.await() == null) throw ProcessingException(ProcessingError.MODEL_UNAVAILABLE)
                }
                val pipeline = TranscribeAudioUseCase(
                    decode = { preprocess(app.files.decode(File(note.path)),Preprocessing()) },
                    vad = engine::speech,
                    diarize = { engine.speakers(it,settings.speakerCount) },
                    recognize = { audio, regions, progress ->
                        if (settings.asrModel == AsrModelType.WHISPER) app.whisper.lock.withLock {
                            val dir = app.whisper.dir.takeIf { app.whisper.installed }
                            if (dir != null) actualModel = AsrModelType.WHISPER
                            engine.recognize(audio,regions,dir,progress)
                        } else engine.recognize(audio,regions,null,progress)
                    },
                    punctuate = app.punctuation::restore,
                    clockMs = { clock }
                )
                val result = pipeline(settings.punctuationEnabled,::update)
                val saveStart = clock
                if (!app.notes.save(id,result.turns,saveStart-start,result.durationMs)) return@withLock Result.success()
                val total = clock-start
                app.notes.saveElapsed(id,total)
                // Отказ внешнего каталога не должен испортить готовую заметку.
                runCatching {
                    val dir = requireNotNull(app.getExternalFilesDir("debug"))
                    val metrics = org.json.JSONObject(result.timings).put("saveMs",total-(saveStart-start)).put("totalMs",total)
                        .put("durationMs",result.durationMs).put("model",actualModel.name)
                        .put("punctuationEnabled",settings.punctuationEnabled)
                        .put("rtf",total.toDouble()/result.durationMs.coerceAtLeast(1))
                    File(dir,"$id.metrics.json").writeText(metrics.toString(2))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { app.notes.requeueInterrupted(id) }
                throw e
            } catch (e: OutOfMemoryError) {
                app.notes.fail(id,ProcessingError.OUT_OF_MEMORY)
            } catch (e: Exception) {
                android.util.Log.e("Transcription","Ошибка обработки $id",e)
                app.notes.fail(id,when(e) {
                    is ProcessingException -> e.reason
                    is java.io.FileNotFoundException -> ProcessingError.MODEL_UNAVAILABLE
                    is android.media.MediaCodec.CodecException -> ProcessingError.UNSUPPORTED_FORMAT
                    else -> ProcessingError.UNKNOWN
                })
            }
            Result.success()
        }
    }
}
