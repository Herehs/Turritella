package ru.steno.presentation.main

import ru.steno.domain.*
import ru.steno.data.audio.RecorderStatus

/** Только для превью экранов. */
object MainPreviewData {
    val empty = MainState(isLoading=false)
    val processing = MainState(isLoading=false, notes=listOf(NoteItem(1,"Совещание",0,84000,ProcessingStatus.PROCESSING,
        TranscriptionProgress(ProcessingStage.RECOGNITION,.52f),null,"")))
}
