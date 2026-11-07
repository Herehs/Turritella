package ru.steno.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class UiMessage { UnsupportedFormat, FileUnreadable, NotEnoughStorage, ExportDone, ExportFailed, TranscriptNotReady,
    MicPermissionDenied, RecordingFailed, RecordingTooShort, WhisperNotInstalled, WhisperInstalled, WhisperDownloadFailed, ModelInUse }
abstract class ScreenModel<S,I,E>(initial: S) : ViewModel() {
    private val current = MutableStateFlow(initial)
    val state = current.asStateFlow()
    private val events = Channel<E>(Channel.BUFFERED)
    val effects = events.receiveAsFlow()
    protected fun reduce(change: (S) -> S) = current.update(change)
    protected fun emit(event: E) { viewModelScope.launch { events.send(event) } }
    abstract fun onIntent(intent: I)
}
