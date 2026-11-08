package ru.steno

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.steno.domain.*
import ru.steno.data.export.jsonTranscript
import kotlinx.serialization.json.*

/** Проверяет склейку реальных алгоритмов на синтетических данных, не качество нейросетей. */
class HackathonPipelineTest {
    @Test fun dialogKeepsSpeakersOriginalTimesAndExactExportSchema() = runBlocking {
        val updates = mutableListOf<Pair<ProcessingStage, Float>>()
        var llmCalls = 0
        val pipeline = TranscribeAudioUseCase(
            decode = { FloatArray(SAMPLE_RATE*8) },
            vad = { listOf(Region(16000,64000),Region(80000,112000)) },
            diarize = { listOf(Region(0,48000,9),Region(48000,80000,4),Region(80000,128000,9)) },
            recognize = { _, regions, progress ->
                progress(1f)
                mergeTurns(regions.mapIndexed { i,r -> Utterance(r.speaker,r.start*1000L/SAMPLE_RATE,r.end*1000L/SAMPLE_RATE,
                    listOf("добрый день","да слушаю","начнем встречу")[i]) })
            },
            punctuate = { turns, progress ->
                llmCalls++
                progress(1f)
                turns.mapIndexed { i,t -> t.copy(text=guardPunctuation(t.text,
                    listOf("Добрый день!","Да, слушаю.","Начнём встречу.")[i])) }
            }, clockMs = {0L},
        )
        val result = pipeline(true) { stage,fraction -> updates+=stage to fraction }
        assertEquals(1,llmCalls)
        assertEquals(8000,result.durationMs)
        assertEquals(listOf(1,2,1),result.turns.map {it.speaker})
        assertEquals(listOf(1000L,3000L,5000L),result.turns.map {it.startMs})
        assertEquals(listOf(3000L,4000L,7000L),result.turns.map {it.endMs})
        assertTrue(updates.zipWithNext().all {(a,b)->a.second<=b.second})
        val export=Json.parseToJsonElement(jsonTranscript(result.turns)).jsonObject
        assertEquals(setOf("transcript"),export.keys)
        export.getValue("transcript").jsonArray.forEach { row ->
            assertEquals(setOf("speaker_id","start_ms","end_ms","text"),row.jsonObject.keys)
        }
    }

    @Test fun punctuationCannotDeleteRepeatOrRewriteWords() {
        for ((source, answer) in listOf("не согласен" to "Согласен.", "сто рублей" to "100 рублей.",
            "да нет" to "Да, да, нет.", "сначала тест потом релиз" to "Сначала релиз, потом тест.")) {
            assertEquals(source,guardPunctuation(source,answer))
        }
    }
}
