package ru.steno

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.steno.domain.*
import ru.steno.data.audio.header
import java.io.RandomAccessFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PipelineTest {
    @Test fun silenceStopsBeforeAsr() {
        val pipeline = TranscribeAudioUseCase({ FloatArray(10) },{ emptyList() },{ error("diarize") },
            { _,_,_ -> error("asr") },{ _,_ -> error("llm") },{ 0 })
        assertEquals(ProcessingError.NO_SPEECH,assertThrows<ProcessingException> { runBlocking { pipeline(true) { _,_ -> } } }.reason)
    }
    @Test fun waveformHeader() {
        val file = File.createTempFile("header",".wav")
        try {
            RandomAccessFile(file,"rw").use { header(it,16000,16000) }
            val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(32036,bytes.getInt(4))
            assertEquals(16000,bytes.getInt(24))
            assertEquals(32000,bytes.getInt(40))
        } finally { file.delete() }
    }
    @Test fun optionalFilterAndSilence() {
        assertArrayEquals(floatArrayOf(0f,0f),preprocess(floatArrayOf(.5f,.5f),Preprocessing(true,true,true)),.0001f)
        val x = floatArrayOf(.5f,.3f)
        assertArrayEquals(x.copyOf(),preprocess(x,Preprocessing()),0f)
    }
}
