package ru.steno.presentation.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import ru.steno.*
import ru.steno.data.audio.RecorderStatus
import ru.steno.presentation.*
import ru.steno.presentation.common.format.formatDate
import java.util.Locale

class RecordingViewModel(private val app: StenoApp) : ScreenModel<RecordingState,RecordingIntent,RecordingEffect>(RecordingState()) {
    private var saveJob: Job? = null
    private val now = System.currentTimeMillis()
    private val defaultTitle = app.getString(R.string.recording_title,formatDate(now,Locale.getDefault()))
    init {
        reduce { it.copy(title=defaultTitle,date=now) }
        viewModelScope.launch { app.recorder.state.collect { r ->
            reduce { it.copy(status=r.status,elapsedMs=r.elapsedMs,amplitudes=r.amplitudes) }
        } }
    }
    private fun start() {
        try { app.recorder.start() } catch(e: Exception) { emit(RecordingEffect.ShowMessage(UiMessage.RecordingFailed)) }
    }
    private fun stop(discard: Boolean) {
        if (saveJob?.isActive == true) return
        if (app.recorder.state.value.status !in listOf(RecorderStatus.PAUSED,RecorderStatus.RECORDING)) return
        reduce { it.copy(showExitDialog=false) }
        // Сохранение должно завершиться и при удалении экрана из back stack.
        saveJob = app.scope.launch {
            try {
                val audio = app.recorder.stop(state.value.title.ifBlank { defaultTitle },discard)
                if (discard) emit(RecordingEffect.NavigateBack)
                else if (audio == null) emit(RecordingEffect.ShowMessage(UiMessage.RecordingTooShort))
                else emit(RecordingEffect.OpenNote(withContext(Dispatchers.IO) { app.add(audio) }))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emit(RecordingEffect.ShowMessage(UiMessage.RecordingFailed)) }
        }
    }
    override fun onIntent(intent: RecordingIntent) {
        when(intent) {
            is RecordingIntent.TitleChanged -> reduce { it.copy(title=intent.text) }
            RecordingIntent.StartClicked -> if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start() else emit(RecordingEffect.RequestMicPermission)
            is RecordingIntent.MicPermissionResult -> {
                reduce { it.copy(micPermissionDenied=!intent.granted) }
                if (intent.granted) start() else emit(RecordingEffect.ShowMessage(UiMessage.MicPermissionDenied))
            }
            RecordingIntent.PauseClicked -> app.recorder.pause(true)
            RecordingIntent.ResumeClicked -> app.recorder.pause(false)
            RecordingIntent.StopClicked,RecordingIntent.ExitSaveClicked -> stop(false)
            RecordingIntent.ExitDiscardClicked -> stop(true)
            RecordingIntent.ExitDialogDismissed -> reduce { it.copy(showExitDialog=false) }
            RecordingIntent.BackClicked -> when {
                saveJob?.isActive == true || state.value.status == RecorderStatus.SAVING -> Unit
                state.value.status == RecorderStatus.IDLE -> emit(RecordingEffect.NavigateBack)
                else -> reduce { it.copy(showExitDialog=true) }
            }
        }
    }
}
