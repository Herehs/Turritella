package ru.steno

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.steno.data.export.jsonTranscript
import ru.steno.data.local.Turn
import ru.steno.domain.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflinePipelineTest {
    /** Параметр audio — путь к реальному диалогу во внешней папке приложения. */
    @Test fun importAndTranscribe() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as StenoApp
        val source = File(requireNotNull(InstrumentationRegistry.getArguments().getString("audio")) { "Передайте -e audio <путь к диалогу>" })
        require(source.isFile)
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME,source.name)
            put(MediaStore.Audio.Media.MIME_TYPE,"audio/wav")
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,values))
        try {
            resolver.openOutputStream(uri)!!.use { output -> source.inputStream().use { it.copyTo(output) } }
            val imported = app.files.import(uri)
            val id = app.add(imported)
            try {
                val done = withTimeout(30 * 60 * 1000L) { app.notes.observe(id).filterNotNull().first {
                    it.status in setOf(ProcessingStatus.DONE,ProcessingStatus.FAILED,ProcessingStatus.CANCELLED)
                } }
                assertEquals(done.error.toString(),ProcessingStatus.DONE,done.status)
                val turns = app.notes.turns(id).first().map(Turn::domain)
                assertTrue(turns.isNotEmpty())
                assertTrue(turns.all { it.startMs >= 0 && it.startMs < it.endMs && it.endMs <= done.durationMs })
                File(app.getExternalFilesDir("debug"),"$id.json").writeText(jsonTranscript(turns))
            } finally { app.delete(id) }
        } finally { resolver.delete(uri,null,null) }
        Unit
    }
}
