package ru.steno.data.engine.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.steno.domain.*
import java.io.File
import java.security.MessageDigest

class Punctuation(private val context: Context) {
    fun prepare(progress: (Float) -> Unit = {}): File {
        val checksum = context.assets.open("models/SHA256SUMS").bufferedReader().useLines { lines ->
            lines.first { it.endsWith("  qwen.task") }.substringBefore(' ')
        }
        val model = File(context.filesDir, "qwen-$checksum.task")
        val expected = context.assets.openFd("models/qwen.task").use { it.length }
        if (model.length() == expected) return model
        val temporary = File(context.filesDir, "qwen.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            context.assets.open("models/qwen.task").use { input ->
                java.security.DigestInputStream(input, digest).use { source ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(262144)
                        var copied = 0L
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            output.write(buffer,0,n)
                            copied += n
                            progress(copied.toFloat()/expected)
                        }
                    }
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == checksum)
            check(temporary.renameTo(model))
            return model
        } finally { temporary.delete() }
    }

    suspend fun restore(turns: List<Utterance>, progress: suspend (Float) -> Unit): List<Utterance> {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(prepare().absolutePath).setMaxTokens(768).build()
        val deadline = android.os.SystemClock.elapsedRealtime() + 180000L
        return LlmInference.createFromOptions(context, options).use { llm ->
            turns.mapIndexed { index, turn ->
                val parts = mutableListOf<String>()
                var part = StringBuilder()
                for (word in turn.text.split(' ')) {
                    if (part.length + word.length > 240 && part.isNotEmpty()) {
                        parts += part.toString()
                        part = StringBuilder()
                    }
                    if (part.isNotEmpty()) part.append(' ')
                    part.append(word)
                }
                if (part.isNotEmpty()) parts += part.toString()
                val restored = parts.map { original ->
                    currentCoroutineContext().ensureActive()
                    val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                    if (remaining <= 0) return@map original
                    val config = LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(1).setTemperature(0f).build()
                    LlmInferenceSession.createFromOptions(llm, config).use { session ->
                        session.addQueryChunk("Расставь знаки препинания и заглавные буквы. Сохрани каждое слово. Верни только исправленный текст.\n$original")
                        val future = session.generateResponseAsync()
                        try {
                            guardPunctuation(original, future.get(minOf(remaining,20000L),java.util.concurrent.TimeUnit.MILLISECONDS))
                        } catch (e: java.util.concurrent.TimeoutException) {
                            session.cancelGenerateResponseAsync()
                            runCatching { future.get() }
                            original
                        }
                    }
                }.joinToString(" ")
                progress((index + 1f) / turns.size)
                turn.copy(text = restored)
            }
        }
    }
}
