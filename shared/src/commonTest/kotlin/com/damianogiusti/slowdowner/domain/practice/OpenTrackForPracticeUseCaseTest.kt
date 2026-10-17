package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The decode leaves the caller's (main) thread; marking the track and applying its settings do not. */
class OpenTrackForPracticeUseCaseTest {

    private val log = mutableListOf<String>()
    private val engine = FakeEngine()
    private val repository = FakePracticeRepository()
    private val loops = FakeLoopRepository()
    private val songA = LibraryTrack("a", "A", "/a.m4a", null, 0)
    private val songB = LibraryTrack("b", "B", "/b.m4a", null, 0)
    private val practised = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true))

    @Test
    fun `the file is decoded on the decode dispatcher and the track marked on the caller's`() = runTest {
        val decode = TrackingDispatcher(StandardTestDispatcher(testScheduler))
        val nowPlaying = RecordingNowPlaying { decode.isRunning }
        repository.saveLastState("b", practised)

        assertTrue(openUseCase(RecordingSources { decode.isRunning }, nowPlaying, decode)(songB))

        assertEquals(listOf("nowPlaying:null on caller", "open /b.m4a on decode", "nowPlaying:b on caller"), log)
        assertEquals(75f, engine.speedPercent)
    }

    @Test
    fun `nothing on the caller's thread sees the new track without its settings`() = runTest {
        val nowPlaying = RecordingNowPlaying { false }
        nowPlaying.set(songA)
        log.clear()
        repository.saveLastState("b", practised)
        loops.setActiveLoop("b", assertNotNull(loops.create("b", "Solo", LoopBounds(0.2f, 0.4f))).id)
        // Other work on the caller's dispatcher, at every chance it gets: what an auto-save would see.
        var sawHalfOpened = false
        val other = launch {
            repeat(1_000) {
                if (nowPlaying.observe().value == songB && engine.practiceCalls.isEmpty()) sawHalfOpened = true
                log += "other"
                yield()
            }
        }

        openUseCase(RecordingSources { false }, nowPlaying, StandardTestDispatcher(testScheduler))(songB)
        other.cancel()

        val decoding = log.subList(log.indexOf("nowPlaying:null on caller"), log.indexOf("nowPlaying:b on caller"))
        assertTrue("other" in decoding, "the caller's thread was blocked during the decode: $log")
        assertFalse(sawHalfOpened)
        assertEquals(listOf("speed:75.0", "pitch:-2.0", "loop:${practised.loop}"), engine.practiceCalls)
    }

    private fun openUseCase(
        sources: AudioSourceRepository,
        nowPlaying: NowPlayingRepository,
        decode: CoroutineDispatcher,
    ): OpenTrackForPracticeUseCase {
        val save = SavePracticeStateUseCase(engine, nowPlaying, repository)
        return OpenTrackForPracticeUseCase(OpenTrackUseCase(sources, nowPlaying, decode), save, engine, repository, loops)
    }

    private inner class RecordingSources(
        private val delegate: FakeAudioSourceRepository = FakeAudioSourceRepository(),
        private val onDecode: () -> Boolean,
    ) : AudioSourceRepository by delegate {
        override fun open(source: AudioSource): Boolean {
            log += "open ${(source as AudioSource.LocalFile).path} on ${if (onDecode()) "decode" else "caller"}"
            return delegate.open(source)
        }
    }

    private inner class RecordingNowPlaying(private val onDecode: () -> Boolean) : NowPlayingRepository {
        private val delegate = InMemoryNowPlayingRepository()
        override fun observe() = delegate.observe()
        override fun set(track: LibraryTrack?) {
            log += "nowPlaying:${track?.id} on ${if (onDecode()) "decode" else "caller"}"
            delegate.set(track)
        }
    }

    /** Knows when it is running a block: the test runs every dispatcher on one thread. */
    private class TrackingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        var isRunning = false
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context, object : Runnable {
                override fun run() {
                    isRunning = true
                    try {
                        block.run()
                    } finally {
                        isRunning = false
                    }
                }
            })
        }
    }
}
