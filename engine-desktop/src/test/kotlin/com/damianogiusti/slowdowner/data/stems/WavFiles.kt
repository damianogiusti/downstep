package com.damianogiusti.slowdowner.data.stems

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 16-bit PCM WAV in [folder]: [samples] holds one array per channel. */
internal fun writeWav(folder: File, name: String, sampleRate: Int, samples: List<FloatArray>): File {
    val channels = samples.size
    val frames = samples.first().size
    val bytes = frames * channels * 2
    val buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
    buffer.putInt(16).putShort(1).putShort(channels.toShort()).putInt(sampleRate).putInt(sampleRate * channels * 2)
    buffer.putShort((channels * 2).toShort()).putShort(16)
    buffer.put("data".toByteArray()).putInt(bytes)
    for (i in 0 until frames) {
        for (channel in 0 until channels) {
            buffer.putShort((samples[channel][i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
    }
    return File(folder, name).apply { writeBytes(buffer.array()) }
}
