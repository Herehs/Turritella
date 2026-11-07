package ru.steno.presentation.note

import ru.steno.domain.*
import ru.steno.data.audio.RecorderStatus

/** Только для превью экранов. */
object NotePreviewData {
    val complete = NoteState(title="Планёрка",durationMs=32000,speakerCount=2,status=ProcessingStatus.DONE,
        utterances=listOf(UtteranceItem(0,1,null,500,2800,"Какие задачи остались на сегодня?"),UtteranceItem(1,2,null,3200,6200,"Осталось проверить офлайн-экспорт.")))
    val processing = NoteState(status=ProcessingStatus.PROCESSING,progress=TranscriptionProgress(ProcessingStage.DIARIZATION,.24f))
    val failed = NoteState(status=ProcessingStatus.FAILED,error=ProcessingError.NO_SPEECH)
}
