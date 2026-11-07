package ru.steno.domain

/** Декодер и движки передаются извне, чтобы проверить весь сценарий без Android и весов. */
class TranscribeAudioUseCase(
    private val decode: suspend () -> FloatArray,
    private val vad: suspend (FloatArray) -> List<Region>,
    private val diarize: suspend (FloatArray) -> List<Region>,
    private val recognize: suspend (FloatArray,List<Region>,suspend (Float) -> Unit) -> List<Utterance>,
    private val punctuate: suspend (List<Utterance>,suspend (Float) -> Unit) -> List<Utterance>,
    private val clockMs: () -> Long
) {
    data class Result(val turns: List<Utterance>, val durationMs: Long,val timings: Map<String,Long>)

    suspend operator fun invoke(punctuationEnabled: Boolean, update: suspend (ProcessingStage,Float) -> Unit): Result {
        val timings = linkedMapOf<String,Long>()
        suspend fun <T> timed(name: String, action: suspend () -> T): T {
            val start = clockMs()
            return action().also { timings[name] = clockMs() - start }
        }
        update(ProcessingStage.PREPARING_AUDIO,.02f)
        val audio = timed("decodeMs",decode)
        val speech = timed("vadMs") { vad(audio) }
        if (speech.isEmpty()) throw ProcessingException(ProcessingError.NO_SPEECH)
        update(ProcessingStage.DIARIZATION,.12f)
        val turns = timed("diarizationMs") { speechTurns(speech,diarize(audio),audio.size) }
        update(ProcessingStage.RECOGNITION,.35f)
        val raw = timed("asrMs") { recognize(audio,turns) { update(ProcessingStage.RECOGNITION,.35f+.4f*it) } }
        if (raw.isEmpty()) throw ProcessingException(ProcessingError.NO_SPEECH)
        val result = timed("llmMs") {
            if (punctuationEnabled) {
                update(ProcessingStage.PUNCTUATION,.75f)
                punctuate(raw) { update(ProcessingStage.PUNCTUATION,.75f+.23f*it) }
            } else raw
        }
        update(ProcessingStage.SAVING,.99f)
        return Result(result,audio.size*1000L/SAMPLE_RATE,timings)
    }
}
