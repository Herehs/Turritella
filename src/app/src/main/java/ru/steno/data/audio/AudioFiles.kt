package ru.steno.data.audio

import android.content.Context
import android.media.*
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import ru.steno.domain.*
import java.io.File
import java.nio.ByteOrder

data class ImportedAudio(val file: File, val title: String, val durationMs: Long)

class AudioFiles(private val context: Context) {
    suspend fun import(uri: Uri): ImportedAudio {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Аудиозапись"
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File.createTempFile("audio-", ".media", dir)
        try {
            val input = resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException()
            input.use { source ->
                file.outputStream().use { output ->
                    val bytes = ByteArray(65536)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = source.read(bytes)
                        if (n < 0) break
                        if (dir.usableSpace < n + 4194304L) throw ProcessingException(ProcessingError.NOT_ENOUGH_STORAGE)
                        output.write(bytes, 0, n)
                    }
                }
            }
            val extractor = MediaExtractor()
            val duration = try {
                extractor.setDataSource(file.path)
                val format = track(extractor).second
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0
            } finally { extractor.release() }
            return ImportedAudio(file, name.substringBeforeLast('.', name).ifBlank { name }, duration)
        } catch (e: Throwable) { file.delete(); throw e }
    }

    private fun track(extractor: MediaExtractor): Pair<Int, MediaFormat> {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return index to format
        }
        throw ProcessingException(ProcessingError.UNSUPPORTED_FORMAT)
    }

    suspend fun decode(file: File): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val (index, input) = track(extractor)
            extractor.selectTrack(index)
            val decoder = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME)!!)
            try {
                decoder.configure(input, null, null, 0)
                decoder.start()
                val chunks = mutableListOf<FloatArray>()
                var total = 0
                var frame = 0L
                var sample = 0L
                var previous = 0f
                var ended = false
                val info = MediaCodec.BufferInfo()
                val watchdog = DecodeWatchdog(android.os.SystemClock.elapsedRealtime())
                while (true) {
                    currentCoroutineContext().ensureActive()
                    watchdog.check(android.os.SystemClock.elapsedRealtime())
                    if (!ended) {
                        val i = decoder.dequeueInputBuffer(10000)
                        if (i >= 0) {
                            val buffer = decoder.getInputBuffer(i)!!
                            val n = extractor.readSampleData(buffer, 0)
                            ended = n < 0
                            decoder.queueInputBuffer(i, 0, n.coerceAtLeast(0), extractor.sampleTime.coerceAtLeast(0),
                                if (ended) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                            watchdog.advanced(android.os.SystemClock.elapsedRealtime())
                            if (!ended) extractor.advance()
                        }
                    }
                    val i = decoder.dequeueOutputBuffer(info, 10000)
                    if (i < 0) continue
                    if (info.size > 0) {
                        watchdog.advanced(android.os.SystemClock.elapsedRealtime())
                        val format = decoder.getOutputFormat(i)
                        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (channels <= 0 || rate <= 0) throw ProcessingException(ProcessingError.UNSUPPORTED_FORMAT)
                        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        val width = when (encoding) {
                            AudioFormat.ENCODING_PCM_FLOAT -> 4
                            AudioFormat.ENCODING_PCM_16BIT -> 2
                            else -> throw ProcessingException(ProcessingError.UNSUPPORTED_FORMAT)
                        }
                        val pcm = decoder.getOutputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN)
                        pcm.position(info.offset)
                        val frames = info.size / (width * channels)
                        val chunk = FloatArray((frames.toLong() * SAMPLE_RATE / rate).toInt() + SAMPLE_RATE / rate + 2)
                        var written = 0
                        repeat(frames) {
                            var mono = 0f
                            repeat(channels) { mono += if (width == 4) pcm.float else pcm.short / 32768f }
                            mono /= channels
                            while (sample * rate <= frame * SAMPLE_RATE) {
                                val delta = (frame * SAMPLE_RATE - sample * rate).toFloat() / SAMPLE_RATE
                                chunk[written++] = mono + (previous - mono) * delta
                                sample++
                            }
                            previous = mono
                            frame++
                        }
                        total += written
                        chunks += chunk.copyOf(written)
                    }
                    decoder.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
                val audio = FloatArray(total)
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(audio, offset)
                    offset += chunk.size
                }
                return audio
            } finally { decoder.release() }
        } finally { extractor.release() }
    }
}
