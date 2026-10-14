package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.source.DesktopAudioSourceRepository
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import com.damianogiusti.slowdowner.domain.source.AudioSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
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
import kotlin.test.assertTrue

/** Through the real C++ engine in manual mode: the test pulls the audio, nothing plays. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackEngineTest {

    private val native = NativeEngine.manual(RATE)
    private val file = toneFile()

    @AfterTest
    fun tearDown() {
        native.close()
        file.delete()
    }

    @Test
    fun `half speed from the slider plays half as fast`() = runTest {
        val engine = openEngine()
        engine.setSpeedFromSlider(2_450)
        assertEquals(50f, engine.speedPercent, 0.01f)
        engine.togglePlayStop()
        playFor(4.0)
        assertEquals(0.2f, engine.position, 0.01f)
    }

    @Test
    fun `A and B buttons make a loop that playback stays in`() = runTest {
        val engine = openEngine()
        engine.togglePlayStop()
        playFor(3.0)
        engine.markLoopStart()
        playFor(1.0)
        engine.markLoopEnd()
        assertTrue(engine.isLoopOn)
        assertEquals(0.295f, engine.loopStart, 0.005f)
        assertEquals(0.395f, engine.loopEnd, 0.005f)
        assertEquals(engine.loopStart, engine.position, 0.001f)
        repeat(30) {
            playFor(0.1)
            assertTrue(engine.position in engine.loopStart - 0.001f..engine.loopEnd + 0.001f, "at ${engine.position}")
        }
    }

    @Test
    fun `turning the loop off keeps its points for later`() = runTest {
        val engine = openEngine()
        engine.setLoop(LoopRegion(0.2f, 0.4f, isOn = true))
        engine.setLoopOn(false)
        assertFalse(engine.isLoopOn)
        assertEquals(0.2f, engine.loopStart)
        engine.setLoopOn(true)
        assertEquals(LoopRegion(0.2f, 0.4f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `the end of the file stops playback and is reported`() = runTest {
        val engine = openEngine()
        val events = mutableListOf<PlaybackEvent>()
        backgroundScope.launch { engine.events.toList(events) }
        engine.seekTo(0.95f)
        engine.togglePlayStop()
        playFor(1.0)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(PlaybackEvent.ReachedEnd in events, "events: $events")
        assertFalse(engine.isPlaying)
    }

    @Test
    fun `a closed engine stops polling and refuses calls`() = runTest {
        val engine = openEngine()
        engine.close()
        advanceTimeBy(200)
        runCurrent()
        assertFailsWith<IllegalStateException> { engine.position }
    }

    @Test
    fun `export writes an MP3 with the current speed`() = runTest {
        val engine = openEngine()
        engine.setSpeedPercent(200f)
        val output = File.createTempFile("desktop-export", ".mp3")
        val export = requireNotNull(native.beginExport(output.path))
        var progress = 0f
        while (true) progress = export.next() ?: break
        assertTrue(export.finish())
        assertTrue(progress > 0.9f, "last progress $progress")
        // 10 s at double speed = 5 s at 192 kbps ≈ 120 kB.
        assertTrue(output.length() in 100_000..140_000, "size ${output.length()}")
        output.delete()
    }

    @Test
    fun `a missing file does not open`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        assertFalse(DesktopAudioSourceRepository(engine).open(AudioSource.LocalFile("/nope/missing.m4a")))
        assertFalse(engine.isOpen)
    }

    private fun TestScope.openEngine(): DesktopPlaybackEngine {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        assertTrue(DesktopAudioSourceRepository(engine).open(AudioSource.LocalFile(file.path)))
        assertEquals(10f, engine.durationSeconds, 0.01f)
        return engine
    }

    private fun playFor(seconds: Double) {
        native.render((seconds * RATE).toInt())
    }

    private companion object {
        const val RATE = 44_100

        /** 10 s, 16-bit stereo, 441 Hz. */
        fun toneFile(): File {
            val frames = 10 * RATE
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
            return File.createTempFile("desktop-engine", ".wav").apply { writeBytes(header.array() + data.array()) }
        }
    }
}

private fun assertEquals(expected: Float, actual: Float, tolerance: Float) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")
