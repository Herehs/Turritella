package ru.steno.data.model.download

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.steno.StenoApp
import ru.steno.domain.*
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.security.MessageDigest

private data class Asset(val name: String,val size: Long,val hash: String)
private val manifest = listOf(
    Asset("small-encoder.int8.onnx",112442483,"4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
    Asset("small-decoder.int8.onnx",262226114,"acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
    Asset("small-tokens.txt",816730,"b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
)
private const val revision = "8f3c18b358db4d1f2fc1eae49d75cd20989e4309"
private const val work = "whisper-download"
private val totalSize = manifest.sumOf { it.size }

class Whisper(val app: StenoApp) {
    val dir = File(app.filesDir,"models/whisper")
    val lock = Mutex()
    private val changed = MutableStateFlow(0L)
    val installed get() = File(dir,"installed").isFile && manifest.all { File(dir,it.name).length() == it.size }
    private val manager get() = WorkManager.getInstance(app)
    val state = combine(manager.getWorkInfosForUniqueWorkFlow(work),changed) { jobs,_ ->
        val job = jobs.firstOrNull { !it.state.isFinished } ?: jobs.lastOrNull()
        when {
            installed -> WhisperModelState.Installed(totalSize)
            job?.state == WorkInfo.State.ENQUEUED -> WhisperModelState.WaitingForNetwork
            job?.state == WorkInfo.State.RUNNING && job.progress.getBoolean("verify",false) -> WhisperModelState.Verifying
            job?.state == WorkInfo.State.RUNNING -> WhisperModelState.Downloading(job.progress.getLong("bytes",0),totalSize)
            job?.state == WorkInfo.State.FAILED -> WhisperModelState.Failed(ModelDownloadError.valueOf(job.outputData.getString("error") ?: "UNKNOWN"))
            else -> WhisperModelState.NotInstalled(totalSize)
        }
    }
    fun download() {
        if (installed) return
        manager.enqueueUniqueWork(work,ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<WhisperWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    suspend fun cancel() { manager.cancelUniqueWork(work).await() }
    suspend fun delete(): Boolean {
        cancel()
        if (!lock.tryLock()) return false
        try {
            app.settings.set("asr",AsrModelType.GIGA_AM)
            if (dir.exists()) check(dir.deleteRecursively())
            changed.value++
        } finally { lock.unlock() }
        return true
    }
}

class WhisperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    private val model = (context.applicationContext as StenoApp).whisper
    private fun notification(bytes: Long): ForegroundInfo {
        val context = applicationContext
        val title = context.getString(ru.steno.R.string.download_whisper)
        context.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
            android.app.NotificationChannel(work,title,android.app.NotificationManager.IMPORTANCE_LOW))
        val notification = androidx.core.app.NotificationCompat.Builder(context,work)
            .setContentTitle(title).setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).setOnlyAlertOnce(true).setProgress(100,(bytes*100/totalSize).toInt(),false).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) ForegroundInfo(703,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(703,notification)
    }
    private fun fail(reason: ModelDownloadError) = Result.failure(workDataOf("error" to reason.name))
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        model.lock.withLock {
            try { setForeground(notification(0)) } catch (e: IllegalStateException) { return@withLock Result.retry() }
            val dir = model.dir.apply { mkdirs() }
            var complete = 0L
            try {
                for (asset in manifest) {
                    val target = File(dir,asset.name)
                    if (target.length() == asset.size) { complete += asset.size; continue }
                    val part = File(dir,asset.name + ".part")
                    if (part.length() > asset.size) check(part.delete())
                    if (dir.usableSpace < asset.size - part.length() + 4194304) return@withLock fail(ModelDownloadError.NOT_ENOUGH_STORAGE)
                    if (part.length() < asset.size) {
                        val url = URL("https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/$revision/${asset.name}")
                        val connection = url.openConnection() as HttpsURLConnection
                        connection.connectTimeout = 20000
                        connection.readTimeout = 30000
                        if (part.length() > 0) connection.setRequestProperty("Range","bytes=${part.length()}-")
                        try {
                            val code = connection.responseCode
                            if (code != 200 && code != 206) throw java.io.IOException("HTTP $code")
                            if (code == 206) check(connection.getHeaderField("Content-Range")?.startsWith("bytes ${part.length()}-") == true)
                            java.io.FileOutputStream(part,code == 206).use { output ->
                                connection.inputStream.use { input ->
                                    val buffer = ByteArray(65536)
                                    var nextUpdate = part.length()
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val n = input.read(buffer)
                                        if (n < 0) break
                                        output.write(buffer,0,n)
                                        if (part.length() >= nextUpdate) {
                                            setProgress(workDataOf("bytes" to complete + part.length()))
                                            setForeground(notification(complete + part.length()))
                                            nextUpdate = part.length() + 1048576
                                        }
                                    }
                                }
                            }
                        } finally { connection.disconnect() }
                    }
                    setProgress(workDataOf("verify" to true))
                    val sha = MessageDigest.getInstance("SHA-256")
                    part.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            sha.update(buffer,0,n)
                        }
                    }
                    if (part.length() != asset.size || sha.digest().joinToString("") { "%02x".format(it) } != asset.hash) {
                        part.delete()
                        return@withLock fail(ModelDownloadError.CHECKSUM_MISMATCH)
                    }
                    check(part.renameTo(target))
                    complete += asset.size
                }
                File(dir,"installed").writeText(revision)
                Result.success()
            } catch (e: java.io.IOException) {
                if (runAttemptCount < 3) Result.retry() else fail(ModelDownloadError.NETWORK)
            }
        }
    }
}
