package ru.steno.domain

import java.util.Locale

data class Region(val start: Int, val end: Int, val speaker: Int = 0)
data class Utterance(val speaker: Int, val startMs: Long, val endMs: Long, val text: String)
enum class ProcessingStage { PREPARING_MODELS, PREPARING_AUDIO, DIARIZATION, RECOGNITION, PUNCTUATION, SAVING }
enum class ProcessingStatus { QUEUED, PROCESSING, DONE, FAILED, CANCELLED }
enum class ProcessingError { UNSUPPORTED_FORMAT, NO_SPEECH, OUT_OF_MEMORY, NOT_ENOUGH_STORAGE, MODEL_UNAVAILABLE, UNKNOWN }
class ProcessingException(val reason: ProcessingError) : Exception(reason.name)
data class TranscriptionProgress(val stage: ProcessingStage, val fraction: Float, val etaMs: Long? = null)
const val SAMPLE_RATE = 16000
private const val MAX_CHUNK = SAMPLE_RATE * 24
private const val SHORT_TURN = SAMPLE_RATE * 350 / 1000
private const val MERGE_GAP_MS = 1000L
private val whiteSpace = Regex("\\s+")
private val word = Regex("[\\p{L}\\p{N}]+")
fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('ё', 'е').trim().replace(whiteSpace, " ")
fun guardPunctuation(source: String, answer: String): String {
    fun words(text: String) = word.findAll(normalize(text)).map { it.value }.toList()
    return if (words(source) == words(answer)) answer.trim() else source
}

/** Убирает пустые интервалы и объединяет пересечения одного говорящего. */
private fun unionRegions(regions: List<Region>, size: Int): List<Region> {
    val valid = regions.map { it.copy(start = it.start.coerceIn(0, size), end = it.end.coerceIn(0, size)) }
        .filter { it.start < it.end }
    return valid.groupBy { it.speaker }.values.flatMap { group ->
        val result = mutableListOf<Region>()
        for (region in group.sortedBy { it.start }) {
            val previous = result.lastOrNull()
            if (previous != null && region.start <= previous.end) {
                result[result.lastIndex] = previous.copy(end = maxOf(previous.end, region.end))
            } else result += region
        }
        result
    }.sortedBy { it.start }
}

/** Интервалы полуоткрытые, единица до вызова ASR — один сэмпл. */
fun speechTurns(speech: List<Region>, speakers: List<Region>, size: Int): List<Region> {
    require(size >= 0)
    val sorted = unionRegions(speakers, size)
    val stable = sorted.mapIndexed { i, turn ->
        val previous = sorted.getOrNull(i - 1)
        val next = sorted.getOrNull(i + 1)
        // Паузы и перекрытия не являются коротким переключением внутри одной реплики.
        if (previous != null && next != null && previous.speaker == next.speaker &&
            previous.end == turn.start && turn.end == next.start && turn.end - turn.start < SHORT_TURN)
            turn.copy(speaker = previous.speaker) else turn
    }
    val diarized = unionRegions(stable, size)
    val voices = unionRegions(speech.map { it.copy(speaker = 0) }, size)
    val chunks = mutableListOf<Region>()
    var first = 0
    for (voice in voices) {
        while (first < diarized.size && diarized[first].end <= voice.start) first++
        for (i in first until diarized.size) {
            val turn = diarized[i]
            if (turn.start >= voice.end) break
            var start = maxOf(voice.start, turn.start)
            val end = minOf(voice.end, turn.end)
            while (start < end) {
                val limit = start + minOf(end - start, MAX_CHUNK)
                chunks += Region(start, limit, turn.speaker)
                start = limit
            }
        }
    }
    return chunks.sortedBy { it.start }
}

fun mergeTurns(turns: List<Utterance>): List<Utterance> {
    val ids = mutableMapOf<Int, Int>()
    val result = mutableListOf<Utterance>()
    for (turn in turns.sortedBy { it.startMs }) {
        if (turn.text.isBlank() || turn.endMs <= turn.startMs) continue
        val id = ids.getOrPut(turn.speaker) { ids.size + 1 }
        val last = result.lastOrNull()
        if (last != null && last.speaker == id && turn.startMs - last.endMs < MERGE_GAP_MS) {
            result[result.lastIndex] = last.copy(endMs = maxOf(last.endMs, turn.endMs), text = last.text + " " + turn.text.trim())
        } else result += turn.copy(speaker = id, text = turn.text.trim())
    }
    return result
}

fun activeUtterance(turns: List<Utterance>, positionMs: Long): Int? {
    var lo = 0
    var hi = turns.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (turns[mid].startMs <= positionMs) lo = mid + 1 else hi = mid
    }
    return (lo - 1).takeIf { it >= 0 && positionMs < turns[it].endMs }
}
