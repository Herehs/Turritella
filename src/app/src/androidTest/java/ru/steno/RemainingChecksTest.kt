package ru.steno

import android.content.Intent
import android.os.PowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.steno.data.audio.RecorderStatus
import ru.steno.domain.guardPunctuation
import java.io.File

@RunWith(AndroidJUnit4::class)
class RemainingChecksTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as StenoApp
    private fun command(text: String) {
        instrumentation.uiAutomation.executeShellCommand(text).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
    }
    @Test fun screenOffRecording() = runBlocking {
        assertEquals("Другая запись уже идёт",RecorderStatus.IDLE,app.recorder.state.value.status)
        app.startActivity(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(2000)
        var output: File? = null
        try {
            instrumentation.runOnMainSync { app.recorder.start() }
            delay(2500)
            val before=app.recorder.state.value.elapsedMs
            command("input keyevent 223")
            delay(10000)
            val interactive=app.getSystemService(PowerManager::class.java).isInteractive
            val after=app.recorder.state.value.elapsedMs
            val audio=app.recorder.stop("Проверка погашенного экрана",false)
            output=audio?.file
            File(app.getExternalFilesDir("debug"),"screen-off-check.txt").writeText(
                "screenInteractive=$interactive\nbeforeMs=$before\nafterMs=$after\ndurationMs=${audio?.durationMs}\nbytes=${output?.length()}")
            assertFalse("Экран должен быть выключен",interactive)
            assertTrue("Запись остановилась в фоне",after-before>=8500)
            assertNotNull(audio)
            val decoded=app.files.decode(audio!!.file)
            assertTrue(decoded.size>=16000*11)
        } finally {
            if(app.recorder.state.value.status!=RecorderStatus.IDLE) app.recorder.stop("",true)
            output?.delete()
            command("input keyevent 224")
        }
    }
    @Test fun punctuationResponse() = runBlocking {
        val path=app.preparation.await()!!
        val options=LlmInference.LlmInferenceOptions.builder().setModelPath(path.path).setMaxTokens(768).build()
        val original="чьих не требуя похвал счастлив уж я надеждой сладкой что дева с трепетом любви посмотрит может быть украдкой на песни грешные мои у лукоморья дуб зеленый"
        val prompt="Расставь знаки препинания и заглавные буквы. Сохрани каждое слово. Верни только исправленный текст.\n$original"
        val report=StringBuilder()
        LlmInference.createFromOptions(app,options).use { llm ->
            for ((name,query) in listOf("plain" to prompt,"chat" to
                "<|im_start|>system\nТы редактор русской пунктуации. Сохраняй слова и их порядок.<|im_end|>\n<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n")) {
                val config=LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(1).setTemperature(0f).build()
                LlmInferenceSession.createFromOptions(llm,config).use { session ->
                    session.addQueryChunk(query)
                    val future=session.generateResponseAsync()
                    val response=try { future.get(30,java.util.concurrent.TimeUnit.SECONDS) }
                        catch(e:java.util.concurrent.TimeoutException) { session.cancelGenerateResponseAsync(); runCatching { future.get() }; "TIMEOUT" }
                    report.appendLine("$name\nRAW=$response\nGUARDED=${guardPunctuation(original,response)}")
                }
            }
        }
        File(app.getExternalFilesDir("debug"),"punctuation-check.txt").writeText(report.toString())
        Unit
    }
}
