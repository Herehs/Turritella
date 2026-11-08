package ru.steno

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.steno.domain.*

class RecognitionRegionsTest {
    @Test fun overlappingVadAndSameSpeakerAreRecognizedOnlyOnce() {
        val regions = speechTurns(listOf(Region(0,20000), Region(10000,32000)),
            listOf(Region(0,24000,7), Region(16000,32000,7)),32000)
        assertEquals(listOf(Region(0,32000,7)),regions)
    }

    @Test fun shortAnswerAcrossPausesKeepsItsSpeaker() {
        val regions = speechTurns(listOf(Region(0,50000)),listOf(
            Region(0,16000,1),Region(20000,23000,2),Region(32000,50000,1)),50000)
        assertEquals(listOf(1,2,1),regions.map { it.speaker })
    }

    @Test fun speakerChangesAndOriginalTimelineSurviveSilence() {
        val regions = speechTurns(listOf(Region(16000,48000),Region(64000,96000)),
            listOf(Region(0,40000,3),Region(40000,96000,8)),96000)
        assertEquals(listOf(Region(16000,40000,3),Region(40000,48000,8),Region(64000,96000,8)),regions)
    }

    @Test fun overlapBetweenDifferentSpeakersIsNotReassigned() {
        assertEquals(listOf(Region(0,20000,1),Region(10000,32000,2)),
            speechTurns(listOf(Region(0,32000)),listOf(Region(0,20000,1),Region(10000,32000,2)),32000))
    }

    @Test fun longTurnIsBoundedWithoutLosingSamples() {
        val length=SAMPLE_RATE*61+7
        val chunks=speechTurns(listOf(Region(-10,length+100)),listOf(Region(0,length,0)),length)
        assertEquals(length,chunks.sumOf {it.end-it.start})
        assertTrue(chunks.all {it.end-it.start <= SAMPLE_RATE*24})
        assertTrue(chunks.zipWithNext().all {(a,b)->a.end==b.start})
        assertEquals(length,chunks.last().end)
    }

    @Test fun malformedAndEmptyRegionsNeverReachAsr() {
        assertTrue(speechTurns(listOf(Region(-10,0),Region(10,5)),listOf(Region(0,20)),20).isEmpty())
        assertTrue(speechTurns(listOf(Region(0,5)),listOf(Region(0,5)),0).isEmpty())
    }
}
