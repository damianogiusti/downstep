package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.stems.demucs.StemHeadroom
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The stems through the real C++ engine in manual mode: the test pulls the audio, nothing plays. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackEngineStemsTest {

    private val native = NativeEngine.manual(RATE)
    private val folder = createTempDirectory("desktop-stems").toFile()

    @AfterTest
    fun tearDown() {
        native.close()
        folder.deleteRecursively()
    }

    @Test
    fun `when stems are opened then they are one source of the same length with every gain at 1`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)

        val result = engine.openStems(stems())

        assertEquals(FileOpenResult.Opened, result)
        assertTrue(engine.isOpen)
        assertEquals(3, native.stemCount)
        assertEquals(listOf(1f, 1f, 1f), engine.stemGains)
        assertEquals(3f, engine.durationSeconds, 0.01f)
    }

    @Test
    fun `when stems are opened then the controls are reported as changed`() = runTest(UnconfinedTestDispatcher()) {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val events = mutableListOf<PlaybackEvent>()
        backgroundScope.launch { engine.events.toList(events) }

        engine.openStems(stems())

        assertTrue(PlaybackEvent.ControlsChanged in events, events.toString())
    }

    @Test
    fun `when every stem is at 1 then the level is the original, not the one with headroom`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        // The stems as the pipeline writes them: the original parts times the headroom.
        engine.openStems(stems())
        engine.togglePlayStop()
        playFor(0.3)

        val level = rms(native.render(RATE))

        assertEquals(originalLevel(), level, 0.1 * originalLevel())
    }

    @Test
    fun `when a stem is muted then only the others are heard`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())
        engine.togglePlayStop()
        engine.setStemGain(0, 0f)
        playFor(0.3)

        val level = rms(native.render(RATE))

        val expected = sqrt((AMPLITUDES[1] * AMPLITUDES[1] + AMPLITUDES[2] * AMPLITUDES[2]) / 2)
        assertEquals(expected, level, 0.1 * expected)
        assertEquals(listOf(0f, 1f, 1f), engine.stemGains)
    }

    @Test
    fun `when a gain is out of range or the stem does not exist then it is clamped or ignored`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.setStemGain(0, 0.5f) // nothing open yet
        assertEquals(emptyList(), engine.stemGains)
        engine.openStems(stems())

        engine.setStemGain(0, 7f)
        engine.setStemGain(1, -2f)
        engine.setStemGain(3, 0f)
        engine.setStemGain(-1, 0f)

        assertEquals(listOf(1f, 0f, 1f), engine.stemGains)
    }

    @Test
    fun `when another source opens then the gains start again at 1`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())
        engine.setStemGain(0, 0.2f)
        engine.setStemGain(2, 0f)

        engine.openStems(stems())
        assertEquals(listOf(1f, 1f, 1f), engine.stemGains)
        engine.togglePlayStop()
        playFor(0.3)
        assertEquals(originalLevel(), rms(native.render(RATE)), 0.1 * originalLevel())

        engine.openStems(stems().take(2))
        assertEquals(listOf(1f, 1f), engine.stemGains)
    }

    @Test
    fun `when a single file opens after stems then it plays as a single file`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())
        val single = File(folder, "single.wav").apply { writeBytes(wav(seconds = 2)) }

        assertEquals(FileOpenResult.Opened, engine.openFile(single.path))
        engine.setStemGain(0, 0f)

        assertEquals(0, native.stemCount)
        assertEquals(emptyList(), engine.stemGains)
        assertEquals(2f, engine.durationSeconds, 0.01f)
        engine.togglePlayStop()
        playFor(0.2)
        assertTrue(rms(native.render(RATE / 2)) > 0.1)
    }

    @Test
    fun `when the file is closed then there are no stems`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())

        engine.closeFile()

        assertEquals(emptyList(), engine.stemGains)
        assertFalse(engine.isOpen)
    }

    @Test
    fun `when stems cannot be opened then the result says why and the open stems stay`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())

        val missing = engine.openStems(listOf(File(folder, "missing.mp3").path))
        val none = engine.openStems(emptyList())

        assertIs<FileOpenResult.Failed>(missing)
        assertIs<FileOpenResult.Failed>(none)
        assertEquals(3, native.stemCount)
        assertEquals(listOf(1f, 1f, 1f), engine.stemGains)
    }

    @Test
    fun `when the budget is smaller than all the stems together then it is TooLong`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val files = stems()
        // A stem of 3 s is 3 * 44100 * 2 * 4 = 1.06 MB: each fits in 2 MB, the three do not.
        native.setMemoryBudget(2_000_000)

        assertEquals(FileOpenResult.TooLong, engine.openStems(files))
        assertFalse(engine.isOpen)
        assertEquals(FileOpenResult.Opened, engine.openStems(files.take(1)))
    }

    @Test
    fun `when the engine is closed then stems are refused`() = runTest {
        val engine = DesktopPlaybackEngine(NativeEngine.manual(RATE), backgroundScope)
        val files = stems()
        engine.close()

        assertIs<FileOpenResult.Failed>(engine.openStems(files))
    }

    @Test
    fun `when closed while stems decode then close waits for the decode`() {
        val files = stems(seconds = 90)
        repeat(6) { round ->
            val scope = CoroutineScope(Dispatchers.Default)
            val engine = DesktopPlaybackEngine(NativeEngine.manual(RATE), scope)
            var opened: FileOpenResult? = null
            val decode = thread { opened = engine.openStems(files) }
            Thread.sleep((round * 13 % 60).toLong())
            engine.close()
            decode.join(60_000)
            assertFalse(decode.isAlive, "the decode never returned")
            assertTrue(opened != null)
            assertIs<FileOpenResult.Failed>(engine.openStems(files))
            scope.cancel()
        }
    }

    @Test
    fun `when the gains differ then an export renders them`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        engine.openStems(stems())
        engine.setStemGain(0, 0f)
        engine.setStemGain(1, 0f)
        val output = File(folder, "export.mp3")
        val export = requireNotNull(native.beginExport(output.path))
        while (export.next() != null) continue
        assertTrue(export.finish())

        // Only the third stem, 0.1 at the original level: 3 s of MP3 at 192 kbps is about 72 kB either way.
        val decoded = requireNotNull(NativePcm.decode(output.path) as? PcmDecodeResult.Decoded).pcm.use { pcm ->
            val left = FloatArray((pcm.frames - RATE).toInt())
            pcm.copy(0, RATE.toLong(), left)
            left
        }
        val expected = AMPLITUDES[2] / sqrt(2.0)
        assertEquals(expected, rms(decoded), 0.1 * expected)
    }

    private fun originalLevel(): Double = sqrt(AMPLITUDES.sumOf { it * it } / 2)

    private fun playFor(seconds: Double) {
        native.render((seconds * RATE).toInt())
    }

    /**
     * Three stems of [seconds], pure tones at 330, 441 and 660 Hz on both channels, written as the pipeline
     * writes them: the original amplitude times [StemHeadroom.GAIN], so that at gain 1 they sum to the original.
     */
    private fun stems(seconds: Int = 3): List<String> = FREQUENCIES.indices.map { k ->
        val file = File(folder, "stem-$k-$seconds.mp3")
        val writer = (NativeStemWriter.open(file.path, StemFormat(RATE, channels = 2)) as StemWriterOpenResult.Opened).writer
        val block = FloatArray(RATE * 2)
        for (second in 0 until seconds) {
            for (i in 0 until RATE) {
                val x = (sin(2 * PI * FREQUENCIES[k] * (second * RATE + i) / RATE) * AMPLITUDES[k] * StemHeadroom.GAIN).toFloat()
                block[i * 2] = x
                block[i * 2 + 1] = x
            }
            assertTrue(writer.write(block))
        }
        writer.close()
        assertTrue(writer.isComplete)
        file.path
    }

    private fun wav(seconds: Int): ByteArray {
        val frames = seconds * RATE
        val data = java.nio.ByteBuffer.allocate(frames * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val sample = (sin(2 * PI * 441 * i / RATE) * 16_000).toInt().toShort()
            data.putShort(sample).putShort(sample)
        }
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(2); putInt(RATE); putInt(RATE * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(frames * 4)
        }
        return header.array() + data.array()
    }

    private companion object {
        const val RATE = 44_100
        val FREQUENCIES = listOf(330.0, 441.0, 660.0)
        val AMPLITUDES = listOf(0.4, 0.3, 0.2)
    }
}

private fun assertEquals(expected: Float, actual: Float, tolerance: Float) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

private fun assertEquals(expected: Double, actual: Double, tolerance: Double) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

private fun rms(samples: FloatArray): Double = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
