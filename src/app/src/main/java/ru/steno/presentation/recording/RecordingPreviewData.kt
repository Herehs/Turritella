package ru.steno.presentation.recording

import ru.steno.domain.*
import ru.steno.data.audio.RecorderStatus

/** Только для превью экранов. */
object RecordingPreviewData {
    val idle = RecordingState(title="Планёрка")
    val recording = idle.copy(status=RecorderStatus.RECORDING,elapsedMs=17950,amplitudes=listOf(.2f,.8f,.4f,.6f,.1f))
    val paused = recording.copy(status=RecorderStatus.PAUSED)
}
