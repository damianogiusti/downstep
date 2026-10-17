package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLoopRepository
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStateUseCasesTest {

    private val engine = FakeEngine()
    private val sources = FakeAudioSourceRepository()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val repository = FakePracticeRepository()
    private val save = SavePracticeStateUseCase(engine, nowPlaying, repository)
    private val loops = FakeLoopRepository()
    private val open = OpenTrackForPracticeUseCase(OpenTrackUseCase(sources, nowPlaying), save, engine, repository, loops)

    private val songA = LibraryTrack("a", "A", "/a.m4a", null, 0)
    private val songB = LibraryTrack("b", "B", "/b.m4a", null, 0)
    private val practised = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true))

    @Test
    fun `when nothing is open then saving does nothing`() = runTest {
        save()

        assertTrue(repository.savedTrackIds.isEmpty())
    }

    @Test
    fun `when a track is open then save stores the engine settings for it`() = runTest {
        nowPlaying.set(songA)
        engine.speed = 75f
        engine.pitch = -2f
        engine.loopStart = 0.2f
        engine.loopEnd = 0.4f
        engine.loopEnabled = true

        save()

        assertEquals(practised, repository.lastState("a"))
    }

    @Test
    fun `when opening a track then the outgoing one is saved first`() = runTest {
        open(songA)
        engine.speed = 60f

        open(songB)

        assertEquals(60f, repository.lastState("a")?.speedPercent)
    }

    @Test
    fun `when the track has a saved state then opening applies it`() = runTest {
        repository.saveLastState("b", practised)
        activeLoop("b", LoopBounds(0.2f, 0.4f))

        assertTrue(open(songB))

        assertEquals(75f, engine.speedPercent)
        assertEquals(-2f, engine.pitchSemitones)
        assertEquals(LoopRegion(0.2f, 0.4f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when the active loop moved after the last save then opening loops the loop`() = runTest {
        // B was moved to 0.55 and the app quit inside the auto-save's pause: the state still says 0.40.
        repository.saveLastState("b", practised)
        activeLoop("b", LoopBounds(0.2f, 0.55f))

        open(songB)

        assertEquals(LoopRegion(0.2f, 0.55f, isOn = true), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
        assertEquals(75f, engine.speedPercent)
    }

    @Test
    fun `when the saved state loops but the track has no active loop then nothing loops`() = runTest {
        repository.saveLastState("b", practised)

        open(songB)

        assertEquals(LoopRegion.WHOLE_FILE, LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when the saved state has the loop off then the active loop is kept but off`() = runTest {
        repository.saveLastState("b", practised.copy(loop = practised.loop.copy(isOn = false)))
        activeLoop("b", LoopBounds(0.2f, 0.4f))

        open(songB)

        assertEquals(LoopRegion(0.2f, 0.4f, isOn = false), LoopRegion(engine.loopStart, engine.loopEnd, engine.isLoopOn))
    }

    @Test
    fun `when the track has no saved state then opening resets to defaults`() = runTest {
        engine.speed = 60f
        engine.pitch = 3f

        open(songB)

        assertEquals(100f, engine.speedPercent)
        assertEquals(0f, engine.pitchSemitones)
        assertFalse(engine.isLoopOn)
    }

    @Test
    fun `when the open fails then no settings are applied`() = runTest {
        sources.openSucceeds = false

        assertFalse(open(songB))
        assertTrue(engine.practiceCalls.isEmpty())
    }

    private suspend fun activeLoop(trackId: String, bounds: LoopBounds) {
        val loop = assertNotNull(loops.create(trackId, "Solo", bounds))
        loops.setActiveLoop(trackId, loop.id)
    }

    @Test
    fun `when changes come in a burst then auto save writes once after they settle`() = runTest {
        nowPlaying.set(songA)
        val job = launch { AutoSavePracticeStateUseCase(engine, save)() }
        runCurrent()

        repeat(5) {
            engine.events.emit(PlaybackEvent.PracticeSettingsChanged)
            advanceTimeBy(100)
        }
        engine.events.emit(PlaybackEvent.PositionChanged)
        advanceTimeBy(600)

        assertEquals(listOf("a"), repository.savedTrackIds)
        job.cancel()
    }
}
