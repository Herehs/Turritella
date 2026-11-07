package ru.steno

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.steno.data.audio.*
import ru.steno.data.local.*
import ru.steno.data.work.Queue
import ru.steno.presentation.presentationModule
import ru.steno.presentation.main.ModelsPreparation
import ru.steno.domain.ProcessingError
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.androidx.workmanager.koin.workManagerFactory

class StenoApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val notes by lazy { Room.databaseBuilder(this, Database::class.java,"notes.db").build().notes() }
    val files by lazy { AudioFiles(this) }
    val settings by lazy { Settings(this) }
    val queue by lazy { Queue(this,notes) }
    val recorder by lazy { Recorder(this) }
    val whisper by lazy { ru.steno.data.model.download.Whisper(this) }
    val punctuation by lazy { ru.steno.data.engine.llm.Punctuation(this) }
    val models = MutableStateFlow<ModelsPreparation>(ModelsPreparation.Preparing(0f))
    lateinit var preparation: Deferred<java.io.File?>
    val ready = CompletableDeferred<Unit>()
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@StenoApp); workManagerFactory(); modules(presentationModule) }
        preparation = scope.async {
            try {
                punctuation.prepare { models.value = ModelsPreparation.Preparing(it) }.also { models.value = ModelsPreparation.Ready }
            } catch (e: Exception) {
                models.value = ModelsPreparation.Failed(ProcessingError.MODEL_UNAVAILABLE)
                null
            }
        }
        scope.launch {
            try { queue.recover(); ready.complete(Unit) }
            catch (e: Exception) { ready.completeExceptionally(e) }
        }
    }
    suspend fun add(audio: ImportedAudio): Long {
        val id = try { notes.insert(Note(path=audio.file.path,title=audio.title,durationMs=audio.durationMs)) }
            catch (e: Throwable) { audio.file.delete(); throw e }
        queue.enqueue(id)
        return id
    }
    suspend fun delete(id: Long) {
        val note = notes.get(id) ?: return
        queue.cancel(id)
        notes.delete(id)
        java.io.File(note.path).delete()
    }
}
