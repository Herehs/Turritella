package ru.steno.data.audio

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.media.*
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import ru.steno.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

enum class RecorderStatus { IDLE, RECORDING, PAUSED, SAVING }
data class Capture(val status: RecorderStatus = RecorderStatus.IDLE,val elapsedMs: Long = 0,
                   val amplitudes: List<Float> = emptyList(),val failed: Boolean = false)

class Recorder(private val app: StenoApp) {
    val state = MutableStateFlow(Capture())
    private var task: Job? = null
    private var audio: AudioRecord? = null
    private var file: File? = null
    private var rate = 16000
    private var samples = 0L
    @Volatile private var running = false
    @Volatile private var paused = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (task != null) return
        val candidates = listOf(16000,48000,44100)
        val sources = listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        val rec = sources.firstNotNullOfOrNull { source -> candidates.firstNotNullOfOrNull { hz ->
            val size = AudioRecord.getMinBufferSize(hz,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            if (size <= 0) return@firstNotNullOfOrNull null
            val record = runCatching { AudioRecord(source,hz,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,
                maxOf(size,hz / 5)) }.getOrNull()
            if (record?.state == AudioRecord.STATE_INITIALIZED) record else { record?.release(); null }
        } } ?: error("Микрофон недоступен")
        val dir = File(app.filesDir,"audio").apply { mkdirs() }
        val output = File.createTempFile("capture-", ".wav",dir)
        try {
            app.startForegroundService(Intent(app,RecordingService::class.java))
            rec.startRecording()
        } catch (e: Exception) {
            rec.release(); output.delete(); app.stopService(Intent(app,RecordingService::class.java)); throw e
        }
        rate = rec.sampleRate
        samples = 0
        paused = false
        running = true
        audio = rec
        file = output
        state.value = Capture(RecorderStatus.RECORDING)
        task = app.scope.launch {
            try {
                RandomAccessFile(output,"rw").use { wav ->
                    header(wav,rate,0)
                    val pcm = ShortArray(rate / 20) // 50 мс на один столбик.
                    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    var synced = 0L
                    while (running) {
                        val n = rec.read(pcm,0,pcm.size)
                        if (!running) break
                        check(n >= 0)
                        if (paused || n == 0) continue
                        bytes.clear()
                        var energy = 0.0
                        for (i in 0 until n) {
                            bytes.putShort(pcm[i])
                            energy += (pcm[i] / 32768.0).pow(2)
                        }
                        wav.write(bytes.array(),0,n * 2)
                        samples += n
                        val db = 10 * log10((energy / n).coerceAtLeast(1e-6))
                        val bars = (state.value.amplitudes + ((db + 60) / 60).toFloat().coerceIn(0f,1f)).takeLast(64)
                        state.value = state.value.copy(elapsedMs=samples * 1000 / rate,amplitudes=bars)
                        if (samples - synced >= rate) { header(wav,rate,samples); synced = samples }
                    }
                    header(wav,rate,samples)
                }
            } catch (e: Exception) {
                state.value = state.value.copy(failed=true)
            } finally {
                runCatching { rec.stop() }
                rec.release()
                app.stopService(Intent(app,RecordingService::class.java))
            }
        }
    }
    fun pause(value: Boolean) {
        if (!running) return
        paused = value
        state.value = state.value.copy(status=if (value) RecorderStatus.PAUSED else RecorderStatus.RECORDING)
    }
    suspend fun stop(title: String, discard: Boolean): ImportedAudio? {
        val worker = task ?: return null
        state.value = state.value.copy(status=RecorderStatus.SAVING)
        running = false
        runCatching { audio?.stop() }
        worker.join()
        task = null
        audio = null
        val output = file!!
        file = null
        val duration = samples * 1000 / rate
        val failed = state.value.failed
        state.value = Capture()
        if (discard || duration < 500 || failed) { output.delete(); check(!failed || discard); return null }
        return ImportedAudio(output,title,duration)
    }
}

internal fun header(file: RandomAccessFile, rate: Int, samples: Long) {
    val size = Math.toIntExact(samples * 2)
    val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(value: String) { b.put(value.toByteArray(Charsets.US_ASCII)) }
    tag("RIFF"); b.putInt(size+36); tag("WAVE"); tag("fmt "); b.putInt(16)
    b.putShort(1); b.putShort(1); b.putInt(rate); b.putInt(rate*2); b.putShort(2); b.putShort(16)
    tag("data"); b.putInt(size)
    file.seek(0); file.write(b.array()); file.seek(44L + size)
}

class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "microphone"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel,getString(R.string.recording),NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).putExtra(EXTRA_OPEN_RECORDING,true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(702,NotificationCompat.Builder(this,channel).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.recording)).setUsesChronometer(true).setOngoing(true).setContentIntent(open).build())
        return START_NOT_STICKY
    }
}
