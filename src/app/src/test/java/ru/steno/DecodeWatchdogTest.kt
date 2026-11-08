package ru.steno

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.steno.data.audio.DecodeWatchdog
import ru.steno.domain.*

class DecodeWatchdogTest {
    @Test fun stalledDecoderFailsInsteadOfBlockingOtherRecordings() {
        val watchdog = DecodeWatchdog(100)
        watchdog.check(30_099)
        assertEquals(ProcessingError.UNSUPPORTED_FORMAT,
            assertThrows<ProcessingException> {watchdog.check(30_100)}.reason)
    }
    @Test fun longAudioIsAllowedWhileDecoderMakesProgress() {
        val watchdog = DecodeWatchdog(0)
        for (i in 1..1000) {
            watchdog.check(i*1000L)
            watchdog.advanced(i*1000L)
        }
    }
}
