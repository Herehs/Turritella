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

/** Интервалы полуоткрытые, единица до вызова ASR — один сэмпл. */
fun speechTurns(speech: List<Region>, speakers: List<Region>, size: Int): List<Region> {
    val sorted = speakers.sortedBy { it.start }
    val stable = sorted.mapIndexed { i, turn ->
        val previous = sorted.getOrNull(i - 1)
        val next = sorted.getOrNull(i + 1)
        if (previous != null && previous.speaker == next?.speaker && turn.end - turn.start < SHORT_TURN)
            turn.copy(speaker = previous.speaker) else turn
    }
    val chunks = mutableListOf<Region>()
    var first = 0
    for (voice in speech.sortedBy { it.start }) {
        while (first < stable.size && stable[first].end <= voice.start) first++
        for (i in first until stable.size) {
            val turn = stable[i]
            if (turn.start >= voice.end) break
            var start = maxOf(0, voice.start, turn.start)
            val end = minOf(size, voice.end, turn.end)
            while (start < end) {
                val limit = minOf(end, start + MAX_CHUNK)
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
