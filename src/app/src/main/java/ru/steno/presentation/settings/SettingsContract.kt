package ru.steno.presentation.settings

import androidx.compose.runtime.Immutable
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.data.audio.RecorderStatus


@Immutable data class SettingsState(
    /** Тема приложения. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Формат следующего экспорта. */
    val exportFormat: ExportFormat = ExportFormat.JSON,
    /** Модель следующей обработки. */
    val asrModel: AsrModelType = AsrModelType.GIGA_AM,
    /** Состояние дополнительной модели. */
    val whisper: WhisperModelState = WhisperModelState.NotInstalled(),
    /** Обрабатывать реплики локальной LLM. */
    val punctuationEnabled: Boolean = true,
    /** Известное число участников, null для автоопределения. */
    val speakerCount: Int? = null,
)

sealed interface SettingsIntent {
    /** Сохранить тему. */
    data class ThemeSelected(val mode: ThemeMode) : SettingsIntent
    /** Выбрать JSON или Markdown. */
    data class ExportFormatSelected(val format: ExportFormat) : SettingsIntent
    /** Выбрать установленную модель. */
    data class AsrModelSelected(val type: AsrModelType) : SettingsIntent
    /** Начать докачку по запросу пользователя. */
    data object DownloadWhisperClicked : SettingsIntent
    /** Приостановить загрузку. */
    data object CancelWhisperDownloadClicked : SettingsIntent
    /** Удалить установленную модель. */
    data object DeleteWhisperClicked : SettingsIntent
    /** Включить или отключить пунктуацию. */
    data class PunctuationToggled(val enabled: Boolean) : SettingsIntent
    /** Указать число голосов. */
    data class SpeakerCountSelected(val count: Int?) : SettingsIntent
}

sealed interface SettingsEffect {
    /** Показать результат действия. */
    data class ShowMessage(val message: UiMessage) : SettingsEffect
}
