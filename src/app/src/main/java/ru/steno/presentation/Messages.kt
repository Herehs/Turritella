package ru.steno.presentation

import androidx.annotation.StringRes
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.steno.R

@get:StringRes
val UiMessage.stringRes: Int
    get() = when (this) {
        UiMessage.UnsupportedFormat -> R.string.message_unsupported_format
        UiMessage.FileUnreadable -> R.string.message_file_unreadable
        UiMessage.NotEnoughStorage -> R.string.message_not_enough_storage
        UiMessage.ExportDone -> R.string.message_export_done
        UiMessage.ExportFailed -> R.string.message_export_failed
        UiMessage.TranscriptNotReady -> R.string.message_transcript_not_ready
        UiMessage.MicPermissionDenied -> R.string.message_mic_permission_denied
        UiMessage.RecordingFailed -> R.string.message_recording_failed
        UiMessage.RecordingTooShort -> R.string.message_recording_too_short
        UiMessage.WhisperNotInstalled -> R.string.message_whisper_not_installed
        UiMessage.WhisperInstalled -> R.string.message_whisper_installed
        UiMessage.WhisperDownloadFailed -> R.string.message_whisper_download_failed
        UiMessage.ModelInUse -> R.string.message_model_in_use
    }

@Composable
fun StenoSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = StenoTheme.colors
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = colors.surface,
            contentColor = colors.text,
            actionColor = colors.accent,
            dismissActionContentColor = colors.textSecondary,
        )
    }
}
