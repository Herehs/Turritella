package ru.steno.presentation.recording

import androidx.compose.runtime.Immutable
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.data.audio.RecorderStatus


@Immutable data class RecordingState(
    /** Название будущей заметки. */
    val title: String = "",
    /** Дата начала записи. */
    val date: Long = 0,
    /** Состояние рекордера. */
    val status: RecorderStatus = RecorderStatus.IDLE,
    /** Записанная длительность без пауз. */
    val elapsedMs: Long = 0,
    /** Нормированные амплитуды от 0 до 1. */
    val amplitudes: List<Float> = emptyList(),
    /** Пользователь отказал в доступе. */
    val micPermissionDenied: Boolean = false,
    /** Показать варианты выхода с активной записью. */
    val showExitDialog: Boolean = false,
)

sealed interface RecordingIntent {
    /** Обновить название. */
    data class TitleChanged(val text: String) : RecordingIntent
    /** Начать запись при наличии разрешения. */
    data object StartClicked : RecordingIntent
    /** Поставить запись на паузу. */
    data object PauseClicked : RecordingIntent
    /** Продолжить запись. */
    data object ResumeClicked : RecordingIntent
    /** Сохранить запись и поставить в очередь. */
    data object StopClicked : RecordingIntent
    /** Передать решение пользователя. */
    data class MicPermissionResult(val granted: Boolean) : RecordingIntent
    /** Обработать возврат. */
    data object BackClicked : RecordingIntent
    /** Выйти с сохранением. */
    data object ExitSaveClicked : RecordingIntent
    /** Выйти и удалить запись. */
    data object ExitDiscardClicked : RecordingIntent
    /** Закрыть подтверждение выхода. */
    data object ExitDialogDismissed : RecordingIntent
}

sealed interface RecordingEffect {
    /** Запросить RECORD_AUDIO. */
    data object RequestMicPermission : RecordingEffect
    /** Заменить экран записи просмотром. */
    data class OpenNote(val id: Long) : RecordingEffect
    /** Вернуться к списку. */
    data object NavigateBack : RecordingEffect
    /** Сообщить результат записи. */
    data class ShowMessage(val message: UiMessage) : RecordingEffect
}
