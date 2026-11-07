package ru.steno.domain

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ExportFormat(val extension: String, val mime: String) { JSON("json","application/json"), MARKDOWN("md","text/markdown") }
enum class AsrModelType { GIGA_AM, WHISPER }
enum class NotesLayout { GRID, LIST }
enum class NoteSort { DATE_DESC, DATE_ASC, TITLE_ASC, TITLE_DESC, DURATION_DESC, DURATION_ASC }
enum class PlaybackSpeed(val rate: Float) { X0_5(0.5f), X0_75(0.75f), X1(1f), X1_5(1.5f), X2(2f) }
data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM, val exportFormat: ExportFormat = ExportFormat.JSON,
    val asrModel: AsrModelType = AsrModelType.GIGA_AM, val layout: NotesLayout = NotesLayout.GRID,
    val sort: NoteSort = NoteSort.DATE_DESC, val punctuationEnabled: Boolean = true, val speakerCount: Int? = null,
    val favoriteNoteIds: Set<Long> = emptySet())

enum class ModelDownloadError { NETWORK, NOT_ENOUGH_STORAGE, CHECKSUM_MISMATCH, UNKNOWN }
sealed interface WhisperModelState {
    data class NotInstalled(val sizeBytes: Long = 375485327L) : WhisperModelState
    data object WaitingForNetwork : WhisperModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : WhisperModelState
    data object Verifying : WhisperModelState
    data class Installed(val sizeBytes: Long) : WhisperModelState
    data class Failed(val error: ModelDownloadError) : WhisperModelState
}
