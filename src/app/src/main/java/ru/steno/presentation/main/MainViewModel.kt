package ru.steno.presentation.main

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.steno.*
import ru.steno.data.audio.RecorderStatus
import ru.steno.domain.*
import ru.steno.presentation.*

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(private val app: StenoApp) : ScreenModel<MainState,MainIntent,MainEffect>(MainState()) {
    private val query = MutableStateFlow("")
    init {
        viewModelScope.launch { app.models.collect { m -> reduce { it.copy(modelsPreparation=m) } } }
        viewModelScope.launch {
            combine(query.debounce(300).map(::normalize).distinctUntilChanged().flatMapLatest(app.notes::search),
                app.settings.flow,app.recorder.state) { notes, settings, recording ->
                val sorted = when(settings.sort) {
                    NoteSort.DATE_DESC -> notes
                    NoteSort.DATE_ASC -> notes.reversed()
                    NoteSort.TITLE_ASC -> notes.sortedBy { it.titleNormalized }
                    NoteSort.TITLE_DESC -> notes.sortedByDescending { it.titleNormalized }
                    NoteSort.DURATION_ASC -> notes.sortedBy { it.durationMs }
                    NoteSort.DURATION_DESC -> notes.sortedByDescending { it.durationMs }
                }
                state.value.copy(notes=sorted.map { n -> NoteItem(n.id,n.title,n.createdAt,n.durationMs,n.status,
                    TranscriptionProgress(n.stage,n.fraction).takeIf { n.status == ProcessingStatus.PROCESSING },
                    n.speakerCount.takeIf { n.status == ProcessingStatus.DONE },n.preview,n.id in settings.favoriteNoteIds) },
                    layout=settings.layout,sort=settings.sort,isLoading=false,hasActiveRecording=recording.status != RecorderStatus.IDLE)
            }.collect { snapshot -> reduce { it.copy(notes=snapshot.notes,layout=snapshot.layout,sort=snapshot.sort,
                isLoading=false,hasActiveRecording=snapshot.hasActiveRecording) } }
        }
    }
    override fun onIntent(intent: MainIntent) {
        when(intent) {
            is MainIntent.FavoriteClicked -> viewModelScope.launch { app.settings.toggleFavorite(intent.id) }
            is MainIntent.QueryChanged -> { reduce { it.copy(query=intent.text) }; query.value=intent.text }
            is MainIntent.LayoutSelected -> viewModelScope.launch { app.settings.set("layout",intent.layout) }
            is MainIntent.SortSelected -> viewModelScope.launch { app.settings.set("sort",intent.sort) }
            is MainIntent.NoteClicked -> emit(MainEffect.OpenNote(intent.id))
            MainIntent.RecordClicked -> emit(MainEffect.OpenRecording)
            MainIntent.ImportAudioClicked -> emit(MainEffect.PickAudio(listOf("audio/*","application/ogg")))
            is MainIntent.AudioPicked -> if (intent.uri != null && !state.value.isImporting) {
                reduce { it.copy(isImporting=true) }
                viewModelScope.launch {
                    try {
                        val id = withContext(Dispatchers.IO) { app.add(app.files.import(Uri.parse(intent.uri))) }
                        emit(MainEffect.OpenNote(id))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { emit(MainEffect.ShowMessage(when {
                        e is ProcessingException && e.reason == ProcessingError.NOT_ENOUGH_STORAGE -> UiMessage.NotEnoughStorage
                        e is java.io.FileNotFoundException || e is SecurityException -> UiMessage.FileUnreadable
                        else -> UiMessage.UnsupportedFormat
                    })) } finally { reduce { it.copy(isImporting=false) } }
                }
            }
            is MainIntent.DeleteNoteClicked -> app.scope.launch { app.delete(intent.id) }
            is MainIntent.CancelProcessingClicked -> app.scope.launch { app.queue.cancel(intent.id) }
        }
    }
}
