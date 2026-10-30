package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.source.DesktopAudioSourceRepository
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
    fun `when closed while polling then the freed engine is never read`() {
        // Without the lock, close() could free the engine during a poll: SIGSEGV in take_events, seen in CI.
        val scope = CoroutineScope(Dispatchers.Default)
        repeat(300) { round ->
            val engine = DesktopPlaybackEngine(NativeEngine.manual(RATE), scope, pollInterval = 1.milliseconds)
            Thread.sleep((round % 4).toLong())
            engine.close()
        }
        scope.cancel()
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
    fun `the poll publishes the position as a state, not as events`() = runTest {
        val engine = openEngine()
        val events = mutableListOf<PlaybackEvent>()
        backgroundScope.launch { engine.events.toList(events) }
        runCurrent()
        engine.togglePlayStop()
        runCurrent()
        events.clear()

        playFor(2.0)
        advanceTimeBy(60)
        runCurrent()

        assertEquals(0.2f, engine.positionFlow.value, 0.01f)
        assertTrue(events.isEmpty(), "events: $events")
    }

    @Test
    fun `a seek moves the position state at once`() = runTest {
        val engine = openEngine()

        engine.seekTo(0.6f)

        assertEquals(0.6f, engine.positionFlow.value, 0.001f)
    }

    @Test
    fun `a pitch step that snaps to the same semitone changes nothing`() = runTest {
        val engine = openEngine()
        val events = mutableListOf<PlaybackEvent>()
        backgroundScope.launch { engine.events.toList(events) }
        runCurrent()
        engine.setPitchSemitones(2f)
        engine.setSpeedPercent(80f)
        runCurrent()
        events.clear()

        engine.setPitchSemitones(2f)
        engine.setSpeedPercent(80f)
        // A drag inside the same semitone: the slider snaps to where it already is.
        engine.setPitchFromSlider(engine.pitchSliderPosition + 1)
        runCurrent()

        assertTrue(events.isEmpty(), "events: $events")
        assertEquals(2f, engine.pitchSemitones)
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
    fun `when volume is normalized then the tone plays at the target loudness`() = runTest {
        val engine = openEngine()
        // 441 Hz at -6.2 dBFS on both channels: about -6.9 LUFS, so 9.1 dB down to -16.
        assertEquals(-6.9, native.loudnessLufs, 0.1)
        assertEquals(-16 - native.loudnessLufs, native.normalizationGainDb, 1e-9)
        engine.togglePlayStop()
        val original = rms(native.render(RATE))

        engine.setVolumeNormalized(true)
        playFor(0.1) // past the switch, which fades out and back in
        val normalized = rms(native.render(RATE))

        assertEquals(native.normalizationGainDb, 20 * log10(normalized / original), 0.2)
    }

    @Test
    fun `when volume normalization is off then the level is the original`() = runTest {
        val engine = openEngine()
        engine.togglePlayStop()
        playFor(0.1) // past the fade in
        val original = rms(native.render(RATE))

        engine.setVolumeNormalized(true)
        playFor(0.5)
        engine.setVolumeNormalized(false)
        playFor(0.1)

        assertEquals(original, rms(native.render(RATE)), 1e-3)
    }

    @Test
    fun `a missing file does not open and is a warning`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val logged = mutableListOf<String>()
        val repository = DesktopAudioSourceRepository(engine, LineLogger(logged))

        assertFalse(repository.open(AudioSource.LocalFile("/nope/missing.m4a")))
        assertFalse(engine.isOpen)
        assertEquals(listOf("W Cannot open track {extension=m4a, reason=unreadable}"), logged)
    }

    @Test
    fun `a file the decoder rejects is an error`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val logged = mutableListOf<String>()
        val notAudio = File.createTempFile("not-audio", ".mp3").apply {
            writeText("not audio")
            deleteOnExit()
        }

        assertFalse(DesktopAudioSourceRepository(engine, LineLogger(logged)).open(AudioSource.LocalFile(notAudio.path)))
        assertEquals(listOf("E Cannot open track {extension=mp3, reason=decoder}"), logged)
    }

    @Test
    fun `the decode runs on the decode dispatcher, not on the caller's thread`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val decodeThreads = mutableListOf<Thread>()
        val decode = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                Thread { decodeThreads += Thread.currentThread(); block.run() }.start()
            }
        }

        val opened = DesktopAudioSourceRepository(engine, ConsoleLogger(), decode).open(AudioSource.LocalFile(file.path))

        assertTrue(opened)
        assertEquals(1, decodeThreads.size)
        assertTrue(decodeThreads.single() !== Thread.currentThread())
    }

    /** One string per line: level, message and properties. */
    private class LineLogger(private val lines: MutableList<String>) : Logger {
        override fun d(message: String, properties: Map<String, String>) { lines += "D $message $properties" }
        override fun i(message: String, properties: Map<String, String>) { lines += "I $message $properties" }
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) { lines += "W $message $properties" }
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) { lines += "E $message $properties" }
    }

    private suspend fun TestScope.openEngine(): DesktopPlaybackEngine {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        assertTrue(DesktopAudioSourceRepository(engine, ConsoleLogger()).open(AudioSource.LocalFile(file.path)))
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

private fun assertEquals(expected: Double, actual: Double, tolerance: Double) =
    assertTrue(abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, was $actual")

private fun rms(samples: FloatArray): Double = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
