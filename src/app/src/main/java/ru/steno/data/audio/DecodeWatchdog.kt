package ru.steno.data.audio

import ru.steno.domain.ProcessingError
import ru.steno.domain.ProcessingException

/** Повреждённый поток не должен навсегда занимать последовательную очередь обработки. */
internal class DecodeWatchdog(startMs: Long, private val timeoutMs: Long = 30_000) {
    private var lastProgress = startMs
    fun advanced(nowMs: Long) { lastProgress = nowMs }
    fun check(nowMs: Long) {
        if (nowMs - lastProgress >= timeoutMs) throw ProcessingException(ProcessingError.UNSUPPORTED_FORMAT)
    }
}
