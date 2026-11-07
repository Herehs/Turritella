package ru.steno.presentation.main

import androidx.compose.runtime.Immutable
import ru.steno.domain.*
import ru.steno.presentation.UiMessage
import ru.steno.data.audio.RecorderStatus

data class NoteItem(val id: Long, val title: String, val createdAt: Long, val durationMs: Long,
    val status: ProcessingStatus, val progress: TranscriptionProgress?, val speakerCount: Int?, val preview: String)
sealed interface ModelsPreparation {
    data object Ready : ModelsPreparation
    data class Preparing(val fraction: Float) : ModelsPreparation
    data class Failed(val error: ProcessingError) : ModelsPreparation
}
@Immutable data class MainState(
    /** Отобранные и отсортированные карточки. */
    val notes: List<NoteItem> = emptyList(),
    /** Текущий запрос поиска. */
    val query: String = "",
    /** Сохранённый вид списка. */
    val layout: NotesLayout = NotesLayout.GRID,
    /** Порядок карточек. */
    val sort: NoteSort = NoteSort.DATE_DESC,
    /** Ожидается первая выборка базы. */
    val isLoading: Boolean = true,
    /** Идёт копирование аудио. */
    val isImporting: Boolean = false,
    /** Готовность встроенных моделей. */
    val modelsPreparation: ModelsPreparation = ModelsPreparation.Ready,
    /** Микрофон занят текущей записью. */
    val hasActiveRecording: Boolean = false,
)

sealed interface MainIntent {
    /** Отправить при изменении поиска. */
    data class QueryChanged(val text: String) : MainIntent
    /** Сохранить вид списка. */
    data class LayoutSelected(val layout: NotesLayout) : MainIntent
    /** Сохранить порядок карточек. */
    data class SortSelected(val sort: NoteSort) : MainIntent
    /** Открыть выбранную заметку. */
    data class NoteClicked(val id: Long) : MainIntent
    /** Запросить выбор файла. */
    data object ImportAudioClicked : MainIntent
    /** Передать результат выбора, включая отмену. */
    data class AudioPicked(val uri: String?) : MainIntent
    /** Открыть запись. */
    data object RecordClicked : MainIntent
    /** Удалить заметку вместе с аудио. */
    data class DeleteNoteClicked(val id: Long) : MainIntent
    /** Остановить обработку этой заметки. */
    data class CancelProcessingClicked(val id: Long) : MainIntent
}

sealed interface MainEffect {
    /** Добавить просмотр заметки в стек. */
    data class OpenNote(val id: Long) : MainEffect
    /** Перейти на экран записи. */
    data object OpenRecording : MainEffect
    /** Открыть системный выбор аудио. */
    data class PickAudio(val mimeTypes: List<String>) : MainEffect
    /** Показать уведомление о результате. */
    data class ShowMessage(val message: UiMessage) : MainEffect
}
