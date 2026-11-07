package ru.steno.data.engine.sherpa

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.*
import ru.steno.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.roundToInt

class SherpaEngine(private val assets: AssetManager) {
    suspend fun speech(audio: FloatArray): List<Region> {
        val model = SileroVadModelConfig(model = "models/vad.onnx")
        val vad = Vad(assets, VadModelConfig(sileroVadModelConfig = model))
        val regions = mutableListOf<Region>()
        fun drain() {
            while (!vad.empty()) {
                val segment = vad.front()
                regions += Region(segment.start, (segment.start + segment.samples.size).coerceAtMost(audio.size))
                vad.pop()
            }
        }
        try {
            for (offset in audio.indices step model.windowSize) {
                currentCoroutineContext().ensureActive()
                val window = FloatArray(model.windowSize)
                audio.copyInto(window, 0, offset, minOf(audio.size, offset + window.size))
                vad.acceptWaveform(window)
                drain()
            }
            vad.flush()
            drain()
            return regions
        } finally { vad.release() }
    }

    suspend fun speakers(audio: FloatArray, count: Int?): List<Region> {
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig("models/segment.onnx")
            ),
            embedding = SpeakerEmbeddingExtractorConfig(model = "models/voice.onnx"),
            clustering = FastClusteringConfig(numClusters = count ?: -1)
        )
        val engine = OfflineSpeakerDiarization(assets, config)
        try {
            return engine.process(audio).map {
                Region((it.start * SAMPLE_RATE).roundToInt(), (it.end * SAMPLE_RATE).roundToInt(), it.speaker)
            }
        } finally { engine.release() }
    }

    suspend fun recognize(audio: FloatArray, regions: List<Region>, whisper: java.io.File?, progress: suspend (Float) -> Unit): List<Utterance> {
        val model = if (whisper == null) OfflineModelConfig(
            nemo = OfflineNemoEncDecCtcModelConfig("models/giga.onnx"),
            tokens = "models/tokens.txt", numThreads = 2
        )
        else OfflineModelConfig(whisper = OfflineWhisperModelConfig(
            encoder = whisper.resolve("small-encoder.int8.onnx").path,
            decoder = whisper.resolve("small-decoder.int8.onnx").path,
            language = "ru", task = "transcribe"), tokens = whisper.resolve("small-tokens.txt").path, numThreads = 2)
        val asr = OfflineRecognizer(if (whisper == null) assets else null, OfflineRecognizerConfig(modelConfig = model))
        try {
            val turns = regions.mapIndexed { index, region ->
                currentCoroutineContext().ensureActive()
                val stream = asr.createStream()
                try {
                    stream.acceptWaveform(audio.copyOfRange(region.start, region.end), SAMPLE_RATE)
                    asr.decode(stream)
                    val text = asr.getResult(stream).text
                    progress((index + 1f) / regions.size)
                    Utterance(region.speaker, region.start * 1000L / SAMPLE_RATE, region.end * 1000L / SAMPLE_RATE, text)
                } finally { stream.release() }
            }
            return mergeTurns(turns)
        } finally { asr.release() }
    }
}
