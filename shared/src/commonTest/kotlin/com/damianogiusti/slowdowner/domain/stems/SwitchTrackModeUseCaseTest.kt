package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.data.stems.InMemoryModeSwitchRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLogger
import com.damianogiusti.slowdowner.fake.FakePracticeRepository
import com.damianogiusti.slowdowner.fake.FakeStemMixer
import com.damianogiusti.slowdowner.fake.FakeStemsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwitchTrackModeUseCaseTest {

    private val engine = FakeEngine()
    private val fakeSources = FakeAudioSourceRepository()
    private val sources = ResettingSources(fakeSources, engine)
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val practice = FakePracticeRepository()
    private val mixer = FakeStemMixer()
    private val song = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val other = LibraryTrack("2", "Other", "/other.m4a", null, 0)
    private val stems = FakeStemsRepository(
        listOf(
            TrackStems("1", "model", TrackMode.FULL_TRACK, StemMix(mapOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 1f))),
        ),
    )
    private val openForPractice = OpenTrackForPracticeUseCase(
        OpenTrackUseCase(sources, nowPlaying, stems, mixer, FakeLogger()),
        SavePracticeStateUseCase(engine, nowPlaying, practice),
        engine,
        practice,
    )
    private val modeSwitch = InMemoryModeSwitchRepository()
    private val switch = SwitchTrackModeUseCase(stems, nowPlaying, sources, engine, openForPractice, modeSwitch)

    private suspend fun playSong(position: Float, isPlaying: Boolean) {
        openForPractice(song)
        engine.position = position
        engine.isPlaying = isPlaying
    }

    @Test
    fun `switching to stems while playing keeps the position and keeps playing`() = runTest {
        playSong(position = 0.4f, isPlaying = true)

        assertEquals(OpenResult.Opened, switch("1", TrackMode.STEMS))

        assertIs<AudioSource.Stems>(sources.current())
        assertEquals(0.4f, engine.position)
        assertTrue(engine.isPlaying)
        assertEquals(TrackMode.STEMS, stems.stems.value["1"]?.mode)
        assertEquals(listOf(StemKind.VOCALS to 0.5f, StemKind.DRUMS to 1f), mixer.calls)
    }

    @Test
    fun `switching back to the full track while playing keeps the position and keeps playing`() = runTest {
        stems.setMode("1", TrackMode.STEMS)
        playSong(position = 0.7f, isPlaying = true)

        assertEquals(OpenResult.Opened, switch("1", TrackMode.FULL_TRACK))

        assertEquals(AudioSource.LocalFile("/song.m4a"), sources.current())
        assertEquals(0.7f, engine.position)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `switching re-applies speed pitch and an active loop`() = runTest {
        val practised = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.6f, isOn = true))
        playSong(position = 0.4f, isPlaying = true)
        engine.setSpeedPercent(practised.speedPercent)
        engine.setPitchSemitones(practised.pitchSemitones)
        engine.setLoop(practised.loop)

        switch("1", TrackMode.STEMS)

        assertEquals(75f, engine.speed)
        assertEquals(-2f, engine.pitch)
        assertTrue(engine.loopEnabled)
        assertEquals(0.2f, engine.loopStart)
        assertEquals(0.6f, engine.loopEnd)
        // The loop is still around the position, which did not move.
        assertEquals(0.4f, engine.position)
        assertEquals(practised, practice.states.value["1"])
    }

    @Test
    fun `switching an inactive loop keeps it inactive`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        engine.setLoop(LoopRegion(0.2f, 0.6f, isOn = false))

        switch("1", TrackMode.STEMS)

        assertFalse(engine.loopEnabled)
        assertEquals(0.2f, engine.loopStart)
        assertEquals(0.6f, engine.loopEnd)
    }

    @Test
    fun `switching while paused stays paused at the same position`() = runTest {
        playSong(position = 0.25f, isPlaying = false)

        assertEquals(OpenResult.Opened, switch("1", TrackMode.STEMS))

        assertFalse(engine.isPlaying)
        assertEquals(0.25f, engine.position)
    }

    @Test
    fun `switching a track that is not open only saves the mode`() = runTest {
        openForPractice(other)
        fakeSources.opened.clear()

        assertNull(switch("1", TrackMode.STEMS))

        assertEquals(TrackMode.STEMS, stems.stems.value["1"]?.mode)
        assertTrue(fakeSources.opened.isEmpty())
        assertEquals(other, nowPlaying.observe().value)
    }

    @Test
    fun `switching with nothing open only saves the mode`() = runTest {
        assertNull(switch("1", TrackMode.STEMS))

        assertEquals(TrackMode.STEMS, stems.stems.value["1"]?.mode)
        assertTrue(fakeSources.opened.isEmpty())
    }

    @Test
    fun `switching to the mode that already plays does not reopen`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        fakeSources.opened.clear()

        assertNull(switch("1", TrackMode.FULL_TRACK))

        assertTrue(fakeSources.opened.isEmpty())
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `switching to stems when the stem files are missing saves the mode and keeps the original`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        stems.missingFiles += "1"
        fakeSources.opened.clear()

        assertNull(switch("1", TrackMode.STEMS))

        assertEquals(TrackMode.STEMS, stems.stems.value["1"]?.mode)
        assertTrue(fakeSources.opened.isEmpty())
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `when the stems fail to open then the original plays on from the same position`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        fakeSources.stemsResult = OpenResult.Failed

        assertEquals(OpenResult.Opened, switch("1", TrackMode.STEMS))

        assertEquals(AudioSource.LocalFile("/song.m4a"), sources.current())
        assertEquals(TrackMode.FULL_TRACK, stems.stems.value["1"]?.mode)
        assertEquals(0.4f, engine.position)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun `when nothing can be reopened then the result says so and nothing resumes`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        fakeSources.result = OpenResult.Failed

        assertEquals(OpenResult.Failed, switch("1", TrackMode.STEMS))

        assertFalse(engine.isPlaying)
        assertNull(nowPlaying.observe().value)
    }

    @Test
    fun `while the source reopens the switch is flagged and now playing is still null`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        val seen = mutableListOf<Triple<Boolean, LibraryTrack?, Boolean>>()
        val watching = ResettingSources(fakeSources, engine) {
            seen += Triple(modeSwitch.observe().value, nowPlaying.observe().value, engine.isPlaying)
        }
        val watched = SwitchTrackModeUseCase(
            stems, nowPlaying, watching, engine,
            OpenTrackForPracticeUseCase(
                OpenTrackUseCase(watching, nowPlaying, stems, mixer, FakeLogger()),
                SavePracticeStateUseCase(engine, nowPlaying, practice),
                engine,
                practice,
            ),
            modeSwitch,
        )

        watched("1", TrackMode.STEMS)

        // The flag is up before now playing goes null, so an auto-save finds no track to store the half-opened engine on.
        assertEquals(listOf(Triple<Boolean, LibraryTrack?, Boolean>(true, null, false)), seen)
        assertFalse(modeSwitch.observe().value)
        assertEquals(song, nowPlaying.observe().value)
    }

    @Test
    fun `when the reopen fails then the switch flag is cleared`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        fakeSources.result = OpenResult.Failed

        switch("1", TrackMode.STEMS)

        assertFalse(modeSwitch.observe().value)
    }

    @Test
    fun `when nothing is reopened then the switch flag never rises`() = runTest {
        playSong(position = 0.4f, isPlaying = true)
        val seen = mutableListOf<Boolean>()
        val watching = ResettingSources(fakeSources, engine)
        val flagged = SwitchTrackModeUseCase(stems, nowPlaying, watching, engine, openForPractice, modeSwitch)

        flagged("1", TrackMode.FULL_TRACK)
        seen += modeSwitch.observe().value

        assertEquals(listOf(false), seen)
    }

    /** What a real engine does on an open: back to the start, stopped, default speed, pitch and loop. */
    private class ResettingSources(
        private val delegate: FakeAudioSourceRepository,
        private val engine: FakeEngine,
        private val onOpen: () -> Unit = {},
    ) : AudioSourceRepository by delegate {
        override suspend fun open(source: AudioSource): OpenResult {
            engine.position = 0f
            engine.isPlaying = false
            engine.speed = 100f
            engine.pitch = 0f
            engine.loopEnabled = false
            engine.loopStart = 0f
            engine.loopEnd = 1f
            onOpen()
            return delegate.open(source)
        }
    }
}
