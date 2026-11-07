package ru.steno.presentation.settings

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.steno.StenoApp
import ru.steno.domain.*
import ru.steno.presentation.*

class SettingsViewModel(private val app: StenoApp) : ScreenModel<SettingsState,SettingsIntent,SettingsEffect>(SettingsState()) {
    init { viewModelScope.launch { combine(app.settings.flow,app.whisper.state) { s,w ->
        SettingsState(s.themeMode,s.exportFormat,s.asrModel,w,s.punctuationEnabled,s.speakerCount)
    }.collect { next -> reduce { next } } } }
    override fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            when(intent) {
                is SettingsIntent.ThemeSelected -> app.settings.set("theme",intent.mode)
                is SettingsIntent.ExportFormatSelected -> app.settings.set("export",intent.format)
                is SettingsIntent.AsrModelSelected -> if (intent.type == AsrModelType.WHISPER && !app.whisper.installed)
                    emit(SettingsEffect.ShowMessage(UiMessage.WhisperNotInstalled)) else app.settings.set("asr",intent.type)
                SettingsIntent.DownloadWhisperClicked -> app.whisper.download()
                SettingsIntent.CancelWhisperDownloadClicked -> app.whisper.cancel()
                SettingsIntent.DeleteWhisperClicked -> if (!app.whisper.delete()) emit(SettingsEffect.ShowMessage(UiMessage.ModelInUse))
                is SettingsIntent.PunctuationToggled -> app.settings.punctuation(intent.enabled)
                is SettingsIntent.SpeakerCountSelected -> app.settings.speakers(intent.count)
            }
        }
    }
}
