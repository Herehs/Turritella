package ru.steno.presentation.settings

import ru.steno.domain.*
import ru.steno.data.audio.RecorderStatus

/** Только для превью экранов. */
object SettingsPreviewData {
    val defaults = SettingsState()
    val download = SettingsState(whisper=WhisperModelState.Downloading(64000000,375485327))
}
