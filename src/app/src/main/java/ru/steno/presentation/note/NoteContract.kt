package ru.steno.presentation.note

import androidx.compose.runtime.Immutable
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.data.audio.RecorderStatus

data class UtteranceItem(val id: Long, val speakerNumber: Int, val speakerName: String?, val startMs: Long, val endMs: Long, val text: String)
data class PlayerUiState(val isReady: Boolean = false,val isPlaying: Boolean = false,val positionMs: Long = 0,val speed: PlaybackSpeed = PlaybackSpeed.X1)
@Immutable data class NoteState(
    /** Название заметки. */
    val title: String = "",
    /** Дата создания. */
    val createdAt: Long = 0,
    /** Длительность записи. */
    val durationMs: Long = 0,
    /** Число распознанных голосов. */
    val speakerCount: Int = 0,
    /** Состояние обработки. */
    val status: ProcessingStatus = ProcessingStatus.QUEUED,
    /** Этап и доля завершения. */
    val progress: TranscriptionProgress? = null,
    /** Причина ошибки при обработке. */
    val error: ProcessingError? = null,
    /** Реплики в хронологическом порядке. */
    val utterances: List<UtteranceItem> = emptyList(),
    /** Индекс реплики в позиции плеера. */
    val activeUtteranceIndex: Int? = null,
    /** Состояние воспроизведения. */
    val player: PlayerUiState = PlayerUiState(),
    /** Выбранный формат экспорта. */
    val exportFormat: ExportFormat = ExportFormat.JSON,
    /** Идёт запись результата в файл. */
    val isExporting: Boolean = false,
)

sealed interface NoteIntent {
    /** Сохранить название с задержкой. */
    data class TitleChanged(val text: String) : NoteIntent
    /** Перемотать к началу реплики. */
    data class UtteranceClicked(val index: Int) : NoteIntent
    /** Переключить воспроизведение. */
    data object PlayPauseClicked : NoteIntent
    /** Изменить скорость. */
    data class SpeedSelected(val speed: PlaybackSpeed) : NoteIntent
    /** Запросить место экспорта. */
    data object ExportClicked : NoteIntent
    /** Передать результат выбора файла. */
    data class ExportDestinationPicked(val uri: String?) : NoteIntent
    /** Отменить обработку. */
    data object CancelProcessingClicked : NoteIntent
    /** Запустить повторную обработку. */
    data object RetryProcessingClicked : NoteIntent
    /** Выбрать позицию в аудио. */
    data class SeekTo(val positionMs: Long) : NoteIntent
    /** Сохранить отображаемое имя. */
    data class RenameSpeaker(val speakerNumber: Int, val name: String) : NoteIntent
    /** Удалить заметку. */
    data object DeleteNoteClicked : NoteIntent
}

sealed interface NoteEffect {
    /** Запустить CreateDocument. */
    data class PickExportDestination(val suggestedName: String, val mimeType: String) : NoteEffect
    /** Показать результат действия. */
    data class ShowMessage(val message: UiMessage) : NoteEffect
    /** Вернуться после удаления. */
    data object NavigateBack : NoteEffect
}
