package ru.steno

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.steno.domain.*
import ru.steno.presentation.common.format.*
import ru.steno.data.export.*

class TranscriptTest {
    @Test fun punctuationPreservesWords() {
        assertEquals("Ёж, идёт!",guardPunctuation("еж идет","Ёж, идёт!"))
        assertEquals("не хочу",guardPunctuation("не хочу","Хочу!"))
        assertEquals("двадцать три",guardPunctuation("двадцать три","23."))
    }
    @Test fun speechIsClippedAndSmoothed() {
        assertEquals(listOf(Region(20,19000,4)),
            speechTurns(listOf(Region(20,19000)),listOf(Region(0,10000,4),Region(10000,11000,9),Region(11000,20000,4)),20000))
    }
    @Test fun chronologicalIdsAndMerge() {
        val turns = mergeTurns(listOf(Utterance(0,3000,4000,"в"),Utterance(9,100,120,"а"),Utterance(9,140,200,"б")))
        assertEquals(listOf(Utterance(1,100,200,"а б"),Utterance(2,3000,4000,"в")),turns)
        assertEquals(null,activeUtterance(turns,200))
        assertEquals(1,activeUtterance(turns,3000))
    }
    @Test fun jsonIsExact() {
        assertEquals("""{"transcript":[{"speaker_id":"speaker_1","start_ms":5,"end_ms":10,"text":"да"}]}""",
            jsonTranscript(listOf(Utterance(1,5,10,"да"))))
        assertEquals("а_б.json",exportName("а/б",ExportFormat.JSON))
    }
    @Test fun timestamps() {
        assertEquals("00:00:00",formatClock(0))
        assertEquals("00:59",formatTimestamp(59999,false))
        assertEquals("1:00:00",formatTimestamp(3600000,true))
        assertEquals("привет еж",normalize(" ПрИвет   Ёж "))
    }
}
