package ru.steno.domain

import kotlin.math.*

/** Меняет собственный буфер декодера. Фильтры по умолчанию выключены до сравнения WER. */
data class Preprocessing(val removeDc: Boolean = false,val highPass: Boolean = false,val normalizePeak: Boolean = false)
fun preprocess(audio: FloatArray, config: Preprocessing): FloatArray {
    if (audio.isEmpty()) return audio
    if (config.removeDc) {
        val mean = audio.average().toFloat()
        for (i in audio.indices) audio[i] -= mean
    }
    if (config.highPass) {
        // Однополюсный фильтр на 80 Гц подавляет низкочастотный гул.
        val alpha = (SAMPLE_RATE / (SAMPLE_RATE + 2 * PI * 80)).toFloat()
        var previous = audio.first()
        var filtered = 0f
        for (i in audio.indices) {
            val value = audio[i]
            filtered = alpha * (filtered + value - previous)
            audio[i] = filtered
            previous = value
        }
    }
    if (config.normalizePeak) {
        val peak = audio.maxOf { abs(it) }
        if (peak > 0) {
            val gain = minOf(4f,.9f/peak)
            for (i in audio.indices) audio[i] *= gain
        }
    }
    return audio
}
