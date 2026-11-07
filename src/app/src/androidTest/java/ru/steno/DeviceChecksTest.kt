package ru.steno

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.steno.data.audio.ImportedAudio
import ru.steno.data.engine.sherpa.SherpaEngine
import ru.steno.domain.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceChecksTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StenoApp

    @Test fun multipleSpeakers() = runBlocking {
        val file = File(app.getExternalFilesDir(null),"steno-two-speakers.wav")
        val audio = app.files.decode(file)
        val engine = SherpaEngine(app.assets)
        val regions = engine.speakers(audio,null)
        assertTrue(regions.isNotEmpty())
        val count = regions.map { it.speaker }.distinct().size
        File(app.getExternalFilesDir("debug"),"speaker-check.txt").writeText(
            "Expected 2 speakers; detected $count\n" + regions.joinToString("\n"))
        assertEquals("Автоматическое число голосов",2,count)
    }

    @Test fun silenceIsReported() = runBlocking {
        val source = File(app.getExternalFilesDir(null),"steno-silence.wav")
        val copy = File(app.filesDir,"audio/check-silence.wav")
        source.copyTo(copy,overwrite=true)
        val id = app.add(ImportedAudio(copy,"Тест тишины",3000))
        try {
            val result = withTimeout(60000) { app.notes.observe(id).filterNotNull().first {
                it.status in setOf(ProcessingStatus.DONE,ProcessingStatus.FAILED)
            } }
            assertEquals(ProcessingError.NO_SPEECH,result.error)
            assertEquals(ProcessingStatus.FAILED,result.status)
        } finally { app.delete(id) }
        Unit
    }

    @Test fun compressedAudioDecodes() = runBlocking {
        val root=app.getExternalFilesDir(null)
        val wav=app.files.decode(File(root,"steno-sample-check.wav"))
        for (name in listOf("steno-sample.mp3","steno-sample.m4a","steno-sample.ogg")) {
            val audio=app.files.decode(File(root,name))
            assertTrue(name,audio.isNotEmpty())
            assertTrue(name,kotlin.math.abs(audio.size-wav.size)<SAMPLE_RATE)
            assertTrue(name,audio.all { it.isFinite() })
        }
    }
}
