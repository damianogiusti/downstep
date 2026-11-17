package com.damianogiusti.slowdowner.data.playback

import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Decode to PCM and write a stem, every native call once (they are `invokeExact` calls: see
 * [NativeEngineTest]) and the round trip that the stem pipeline relies on.
 */
class NativePcmTest {

    private val files = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        files.forEach { it.delete() }
    }

    @Test
    fun `when a stereo file is decoded then rate, channels, frames and samples are those of the file`() {
        val file = wavFile(channels = 2, frames = 10_001)

        decode(file).use { pcm ->
            assertEquals(RATE, pcm.sampleRate)
            assertEquals(2, pcm.channels)
            assertEquals(10_001L, pcm.frames)
            val left = FloatArray(500)
            assertEquals(500, pcm.copy(0, 1_000, left))
            assertEquals(tone(1_000, channel = 0), left[0], 1e-4f)
            assertEquals(tone(1_499, channel = 0), left[499], 1e-4f)
            val right = FloatArray(500)
            assertEquals(500, pcm.copy(1, 1_000, right))
            assertEquals(tone(1_250, channel = 1), right[250], 1e-4f)
        }
    }

    @Test
    fun `when a mono file is decoded then it stays mono and channel 1 reads channel 0`() {
        val file = wavFile(channels = 1, frames = 5_000)

        decode(file).use { pcm ->
            assertEquals(1, pcm.channels)
            val first = FloatArray(100)
            val second = FloatArray(100)
            pcm.copy(0, 0, first)
            pcm.copy(1, 0, second)
            assertTrue(first.contentEquals(second))
            assertTrue(first.any { abs(it) > 0.1f })
        }
    }

    @Test
    fun `when the range runs past the end then fewer samples are copied`() {
        val file = wavFile(channels = 2, frames = 1_000)

        decode(file).use { pcm ->
            val out = FloatArray(100)
            assertEquals(10, pcm.copy(0, 990, out))
            assertEquals(0, pcm.copy(0, 1_000, out))
            assertEquals(0, pcm.copy(0, -1, out))
        }
    }

    @Test
    fun `when the destination is native memory then the same samples arrive`() {
        val file = wavFile(channels = 2, frames = 2_000)

        decode(file).use { pcm ->
            Arena.ofConfined().use { arena ->
                val segment = arena.allocate(300 * 4L, 4)
                assertEquals(300L, pcm.copy(1, 700, segment))
                assertEquals(tone(700, channel = 1), segment.getAtIndex(JAVA_FLOAT, 0), 1e-4f)
                assertEquals(tone(999, channel = 1), segment.getAtIndex(JAVA_FLOAT, 299), 1e-4f)
            }
        }
    }

    @Test
    fun `when the file is not audio then decode fails`() {
        val notAudio = File.createTempFile("not-audio", ".m4a").apply { writeText("not audio") }
        files += notAudio

        assertIs<PcmDecodeResult.Failed>(NativePcm.decode(notAudio.path))
    }

    @Test
    fun `when the budget is smaller than the decoded audio then decode is too long`() {
        val file = wavFile(channels = 2, frames = 2 * RATE)

        assertIs<PcmDecodeResult.TooLong>(NativePcm.decode(file.path, memoryBudgetBytes = 1_000))
    }

    @Test
    fun `when the pcm is closed then reading it fails and closing again is harmless`() {
        val pcm = decode(wavFile(channels = 2, frames = 1_000))

        pcm.close()
        pcm.close()

        assertFailsWith<IllegalStateException> { pcm.frames }
    }

    @Test
    fun `when a stem is written and decoded back then it has the same frames at the same place`() {
        // 468_940 and 882_777 are lengths where a constant-bitrate MP3 reads 1152 frames too long on macOS.
        for ((channels, frames) in listOf(2 to 882_777, 1 to 468_940, 2 to 20_000)) {
            val stem = tempFile(".mp3")
            val clickAt = 7_001
            val source = FloatArray(frames * channels)
            for (c in 0 until channels) source[clickAt * channels + c] = 0.8f

            val writer = openWriter(stem, StemFormat(RATE, channels))
            // Odd blocks, as a caller that separates segment by segment would write them.
            var done = 0
            while (done < frames) {
                val n = minOf(30_011, frames - done)
                assertTrue(writer.write(source.copyOfRange(done * channels, (done + n) * channels)))
                done += n
            }
            writer.close()
            assertTrue(writer.isComplete)

            decode(stem).use { pcm ->
                assertEquals(channels, pcm.channels)
                assertEquals(RATE, pcm.sampleRate)
                assertEquals(frames.toLong(), pcm.frames, "frames of the decoded stem")
                val around = FloatArray(600)
                pcm.copy(0, clickAt - 300L, around)
                val peakAt = around.indices.maxBy { abs(around[it]) }
                assertEquals(300, peakAt, "the click moved")
            }
        }
    }

    @Test
    fun `when samples come from native memory then the stem has them`() {
        val stem = tempFile(".mp3")
        val writer = openWriter(stem, StemFormat(RATE, channels = 2))

        Arena.ofConfined().use { arena ->
            val block: MemorySegment = arena.allocate(4_410 * 2 * 4L, 4)
            repeat(4_410 * 2) { block.setAtIndex(JAVA_FLOAT, it.toLong(), 0.25f) }
            assertTrue(writer.write(block))
        }
        writer.close()

        assertTrue(writer.isComplete)
        decode(stem).use { assertEquals(4_410L, it.frames) }
    }

    @Test
    fun `when the format is not supported then the writer does not open`() {
        val stem = tempFile(".mp3")

        assertIs<StemWriterOpenResult.UnsupportedFormat>(NativeStemWriter.open(stem.path, StemFormat(RATE, channels = 3)))
        assertIs<StemWriterOpenResult.UnsupportedFormat>(NativeStemWriter.open(stem.path, StemFormat(96_000, channels = 2)))
    }

    @Test
    fun `when the folder does not exist then the writer cannot create the file`() {
        val missing = File(File(System.getProperty("java.io.tmpdir")), "native-pcm-no-such-folder/stem.mp3")

        assertIs<StemWriterOpenResult.CannotCreateFile>(NativeStemWriter.open(missing.path, StemFormat(RATE, channels = 2)))
    }

    @Test
    fun `when the writer is closed then writing fails and closing again is harmless`() {
        val writer = openWriter(tempFile(".mp3"), StemFormat(RATE, channels = 1))

        writer.close()
        writer.close()

        assertFailsWith<IllegalStateException> { writer.write(FloatArray(10)) }
    }

    @Test
    fun `when the samples are not whole frames then write rejects them`() {
        val writer = openWriter(tempFile(".mp3"), StemFormat(RATE, channels = 2))

        assertFailsWith<IllegalArgumentException> { writer.write(FloatArray(3)) }
        writer.close()
    }

    private fun decode(file: File): NativePcm = assertIs<PcmDecodeResult.Decoded>(NativePcm.decode(file.path)).pcm

    private fun openWriter(file: File, format: StemFormat): NativeStemWriter =
        assertIs<StemWriterOpenResult.Opened>(NativeStemWriter.open(file.path, format)).writer

    private fun tempFile(suffix: String): File = File.createTempFile("native-pcm", suffix).also { files += it }

    /** 16-bit WAV, 441 Hz, a different phase on each channel. */
    private fun wavFile(channels: Int, frames: Int): File {
        val data = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i -> repeat(channels) { c -> data.putShort(sample16(i, c)) } }
        val bytes = frames * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(channels.toShort()); putInt(RATE); putInt(RATE * channels * 2)
            putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }
        return tempFile(".wav").apply { writeBytes(header.array() + data.array()) }
    }

    private companion object {
        const val RATE = 44_100

        fun sample16(frame: Int, channel: Int): Short = (0.5 * sin(2 * PI * 441 * frame / RATE + channel) * 32768).toInt().toShort()

        fun tone(frame: Int, channel: Int): Float = sample16(frame, channel) / 32768f
    }
}
