package com.damianogiusti.slowdowner.data.playback

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every native call, once. They are `invokeExact` calls: a cast or an argument type that does not
 * match the function's descriptor only fails at run time (WrongMethodTypeException), so each one
 * has to run somewhere.
 */
class NativeEngineTest {

    private val file = toneFile()

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `every getter and setter goes through`() {
        val native = NativeEngine.manual(RATE)
        assertEquals(RATE, native.sampleRate)
        assertFalse(native.isOpen)
        assertEquals(0, native.peaks(0.0, 1.0, 16).size)
        assertTrue(native.open(file.path))
        assertTrue(native.isOpen)
        assertEquals(2.0, native.durationSeconds, 0.01)
        val peaks = native.peaks(0.0, 1.0, 16)
        assertEquals(16, peaks.size)
        // The tone peaks at 16000 of 32768.
        assertTrue(peaks.all { abs(it - 0.488f) < 0.01f }, peaks.joinToString())

        native.setSpeed(1.0)
        native.setPitch(0.0)
        native.setMix(0.5)
        native.setEqEnabled(true)
        repeat(NativeEngine.EQ_BANDS) { native.setEqBand(it, 0.0) }
        native.setPreamp(0.0)
        native.setOutputGain(1.0)
        native.setLoop(0.0, 1.0, enabled = false)
        native.seek(0.25)
        assertEquals(0.25, native.position, 0.001)
        native.setLoopAndSeek(0.5, 0.75, enabled = true, position = 0.5)
        assertEquals(0.5, native.position, 0.001)

        native.setPlaying(true)
        assertTrue(native.isPlaying)
        val audio = native.render(RATE / 10)
        assertEquals(RATE / 10 * 2, audio.size)
        assertTrue(audio.any { abs(it) > 0.01f }, "plays silence")
        assertTrue(native.position in 0.5..0.75)
        assertEquals(0, native.takeEvents())

        native.setPlaying(false)
        assertFalse(native.isPlaying)
        native.closeFile()
        assertFalse(native.isOpen)
        native.close()
    }

    @Test
    fun `the end of the file is an event`() {
        NativeEngine.manual(RATE).use { native ->
            assertTrue(native.open(file.path))
            native.seek(0.9)
            native.setPlaying(true)
            native.render(RATE)
            assertEquals(NativeEngine.EVENT_REACHED_END, native.takeEvents() and NativeEngine.EVENT_REACHED_END)
        }
    }

    @Test
    fun `an export runs to the end`() {
        NativeEngine.manual(RATE).use { native ->
            assertTrue(native.open(file.path))
            val output = File.createTempFile("native-export", ".mp3")
            val export = assertNotNull(native.beginExport(output.path))
            var chunks = 0
            while (export.next() != null) chunks++
            assertTrue(chunks > 0)
            assertTrue(export.finish())
            assertTrue(output.length() > 10_000, "size ${output.length()}")
            output.delete()
        }
    }

    @Test
    fun `a range export lasts the range at the current speed`() {
        NativeEngine.manual(RATE).use { native ->
            assertTrue(native.open(file.path))
            native.setSpeed(0.5)
            val output = File.createTempFile("native-export-range", ".mp3")
            // Half of a 2 s file at half speed: 2 s.
            val export = assertNotNull(native.beginExport(output.path, start = 0.25, end = 0.75))
            val progress = generateSequence { export.next() }.toList()
            assertTrue(export.finish())
            assertTrue(progress.zipWithNext().all { (a, b) -> b >= a }, progress.joinToString())
            assertTrue(progress.last() in 0.9f..1f, progress.joinToString())
            NativeEngine.manual(RATE).use { reader ->
                assertTrue(reader.open(output.path))
                // LAME's delay and padding add about 50 ms.
                assertEquals(2.0, reader.durationSeconds, 0.1)
            }
            output.delete()
        }
    }

    @Test
    fun `an empty range exports nothing`() {
        NativeEngine.manual(RATE).use { native ->
            assertTrue(native.open(file.path))
            val output = File.createTempFile("native-export-range", ".mp3").apply { deleteOnExit() }
            assertNull(native.beginExport(output.path, start = 0.5, end = 0.5))
        }
    }

    @Test
    fun `nothing open exports nothing`() {
        NativeEngine.manual(RATE).use { native ->
            assertNull(native.beginExport(File.createTempFile("native-export", ".mp3").apply { deleteOnExit() }.path))
            assertNull(native.beginExport(File.createTempFile("native-export", ".mp3").apply { deleteOnExit() }.path, 0.1, 0.2))
        }
    }

    private companion object {
        const val RATE = 44_100

        /** 2 s, 16-bit stereo, 441 Hz. */
        fun toneFile(): File {
            val frames = 2 * RATE
            val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { i ->
                val sample = (sin(2 * PI * 441 * i / RATE) * 16_000).toInt().toShort()
                data.putShort(sample).putShort(sample)
            }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(2); putInt(RATE); putInt(RATE * 4); putShort(4); putShort(16)
                put("data".toByteArray()); putInt(frames * 4)
            }
            return File.createTempFile("native-engine", ".wav").apply { writeBytes(header.array() + data.array()) }
        }
    }
}
