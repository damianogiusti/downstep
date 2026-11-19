package com.damianogiusti.slowdowner.ui.stems

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.data.stems.InMemoryModeSwitchRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.stems.ObserveTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.SetStemGainUseCase
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.SwitchTrackModeUseCase
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.damianogiusti.slowdowner.ui.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.ui.fake.FakePlaybackEngine
import com.damianogiusti.slowdowner.ui.fake.FakePracticeRepository
import com.damianogiusti.slowdowner.ui.fake.FakeStemsRepository
import com.damianogiusti.slowdowner.ui.fake.RecordingStemMixer
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.error_cannot_open
import com.damianogiusti.slowdowner.ui.resources.error_track_too_long
import com.damianogiusti.slowdowner.ui.resources.stem_bass
import com.damianogiusti.slowdowner.ui.resources.stem_drums
import com.damianogiusti.slowdowner.ui.resources.stem_vocals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InstrumentsViewModelTest {

    private val stems = FakeStemsRepository()
    private val sources = FakeAudioSourceRepository(OpenResult.Opened)
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val engine = FakePlaybackEngine().apply { isOpen = true }
    private val mixer = RecordingStemMixer()
    private val practice = FakePracticeRepository()
    private val song = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val other = LibraryTrack("2", "Other", "/other.m4a", null, 0)
    private val plain = LibraryTrack("3", "Plain", "/plain.m4a", null, 0)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        stems.add("1", TrackMode.STEMS, mapOf(StemKind.BASS to 0.8f, StemKind.VOCALS to 0.5f, StemKind.DRUMS to 1f))
        stems.add("2", TrackMode.STEMS, mapOf(StemKind.VOCALS to 0.3f))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val openForPractice = OpenTrackForPracticeUseCase(
        OpenTrackUseCase(sources, nowPlaying, stems, mixer, ConsoleLogger()),
        SavePracticeStateUseCase(engine, nowPlaying, practice),
        engine,
        practice,
    )

    private fun viewModel() = InstrumentsViewModel(
        InstrumentsUseCases(
            observeNowPlaying = ObserveNowPlayingUseCase(nowPlaying),
            observeStems = ObserveTrackStemsUseCase(stems),
            switchMode = SwitchTrackModeUseCase(stems, nowPlaying, sources, engine, openForPractice, InMemoryModeSwitchRepository()),
            setGain = SetStemGainUseCase(stems, mixer, sources, nowPlaying),
        ),
    )

    /** Keeps the state collected, as the player does to decide about the action. */
    private class Observer(viewModel: InstrumentsViewModel, scope: TestScope) {
        var state: InstrumentsUiState = InstrumentsUiState.Unavailable
        val states = mutableListOf<InstrumentsUiState>()

        init {
            scope.backgroundScope.launch { viewModel.state.collect { state = it; states += it } }
        }

        val ready get() = assertIs<InstrumentsUiState.Ready>(state)
    }

    private fun TestScope.collectMessages(viewModel: InstrumentsViewModel) = mutableListOf<UiMessage>().also { list ->
        backgroundScope.launch { viewModel.messages.collect { list += it } }
        runCurrent()
    }

    private suspend fun TestScope.opened(track: LibraryTrack = song, viewModel: InstrumentsViewModel = viewModel()): Pair<InstrumentsViewModel, Observer> {
        openForPractice(track)
        val observer = Observer(viewModel, this)
        runCurrent()
        return viewModel to observer
    }

    // runCurrent, not advanceUntilIdle: the collector lives in backgroundScope, which "idle" ignores.

    @Test
    fun `when nothing is open then the panel is unavailable`() = runTest {
        val observer = Observer(viewModel(), this)
        runCurrent()

        assertEquals(InstrumentsUiState.Unavailable, observer.state)
    }

    @Test
    fun `when the open track is not separated then the panel is unavailable`() = runTest {
        val (_, observer) = opened(plain)

        assertEquals(InstrumentsUiState.Unavailable, observer.state)
    }

    @Test
    fun `when the open track is separated then the stems show in the fixed order with their percent`() = runTest {
        val (_, observer) = opened()

        val ready = observer.ready
        assertEquals("1", ready.trackId)
        assertEquals(TrackMode.STEMS, ready.mode)
        assertEquals(listOf(StemKind.VOCALS, StemKind.DRUMS, StemKind.BASS), ready.stems.map { it.kind })
        assertEquals(listOf(Res.string.stem_vocals, Res.string.stem_drums, Res.string.stem_bass), ready.stems.map { it.name })
        assertEquals(listOf(50, 100, 80), ready.stems.map { it.percent })
        assertTrue(ready.areStemControlsEnabled)
        assertFalse(ready.isSwitching)
    }

    @Test
    fun `when the track is not separated then no intent does anything`() = runTest {
        val (viewModel, _) = opened(plain)

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        viewModel.onGainChanged(StemKind.VOCALS, 10)
        viewModel.onMuteToggled(StemKind.VOCALS)
        runCurrent()

        assertTrue(stems.savedGains.isEmpty())
        assertTrue(mixer.calls.filter { it.second != 0.5f }.isEmpty())
    }

    @Test
    fun `when the mode changes to the full track then the track plays its file and the state says so`() = runTest {
        val (viewModel, observer) = opened()

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        assertEquals(TrackMode.FULL_TRACK, observer.ready.mode)
        assertEquals(AudioSource.LocalFile("/song.m4a"), sources.current())
        assertFalse(observer.ready.isSwitching)
        assertFalse(observer.ready.areStemControlsEnabled)
    }

    @Test
    fun `when the mode changes back to the instruments then the track plays its stems`() = runTest {
        val (viewModel, observer) = opened()
        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        viewModel.onModeSelected(TrackMode.STEMS)
        runCurrent()

        assertEquals(TrackMode.STEMS, observer.ready.mode)
        assertIs<AudioSource.Stems>(sources.current())
        assertTrue(observer.ready.areStemControlsEnabled)
    }

    @Test
    fun `while the track reopens then the panel stays and its controls wait`() = runTest {
        val (viewModel, observer) = opened()
        sources.decode = CompletableDeferred()

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        // The reopen closed the track for its decode: the panel does not vanish with it.
        assertTrue(observer.ready.isSwitching)
        assertFalse(observer.ready.areStemControlsEnabled)
        assertEquals(listOf(50, 100, 80), observer.ready.stems.map { it.percent })
    }

    @Test
    fun `while the track reopens then another mode request is ignored`() = runTest {
        val (viewModel, observer) = opened()
        val decode = CompletableDeferred<Unit>()
        sources.decode = decode
        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        viewModel.onModeSelected(TrackMode.STEMS)
        decode.complete(Unit)
        runCurrent()

        assertEquals(TrackMode.FULL_TRACK, observer.ready.mode)
    }

    @Test
    fun `when the reopen ends then the controls are back`() = runTest {
        val (viewModel, observer) = opened()
        val decode = CompletableDeferred<Unit>()
        sources.decode = decode
        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        decode.complete(Unit)
        runCurrent()

        assertFalse(observer.ready.isSwitching)
    }

    @Test
    fun `when the reopen fails then the panel is unavailable because nothing is open`() = runTest {
        val (viewModel, observer) = opened()
        sources.result = OpenResult.Failed

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        assertEquals(InstrumentsUiState.Unavailable, observer.state)
    }

    @Test
    fun `when the reopen fails then the message says it cannot open`() = runTest {
        val (viewModel, _) = opened()
        val messages = collectMessages(viewModel)
        sources.result = OpenResult.Failed

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.error_cannot_open, listOf("Song"))), messages)
    }

    @Test
    fun `when the reopen is too long then the message says it is too long`() = runTest {
        val (viewModel, _) = opened()
        val messages = collectMessages(viewModel)
        sources.result = OpenResult.TooLong

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.error_track_too_long, listOf("Song"))), messages)
    }

    @Test
    fun `when the reopen works then there is no message`() = runTest {
        val (viewModel, _) = opened()
        val messages = collectMessages(viewModel)

        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `a gain change plays at once and is saved`() = runTest {
        val (viewModel, observer) = opened()
        mixer.calls.clear()

        viewModel.onGainChanged(StemKind.DRUMS, 40)
        runCurrent()

        assertEquals(listOf(StemKind.DRUMS to 0.4f), mixer.calls)
        assertEquals(listOf(Triple("1", StemKind.DRUMS, 0.4f)), stems.savedGains)
        assertEquals(40, observer.ready.stems.first { it.kind == StemKind.DRUMS }.percent)
    }

    @Test
    fun `the slider shows the dragged value before the database answers`() = runTest {
        val (viewModel, observer) = opened()
        val gate = CompletableDeferred<Unit>()
        stems.saveGate = gate

        viewModel.onGainChanged(StemKind.DRUMS, 40)
        runCurrent()

        // Saved is still 100 %, and the sound already follows the finger.
        assertEquals(40, observer.ready.stems.first { it.kind == StemKind.DRUMS }.percent)
        assertEquals(StemKind.DRUMS to 0.4f, mixer.calls.last())
        gate.complete(Unit)
        runCurrent()
        assertEquals(40, observer.ready.stems.first { it.kind == StemKind.DRUMS }.percent)
    }

    @Test
    fun `a burst of gain changes ends on the last value in the sound and in the database`() = runTest {
        val (viewModel, observer) = opened()
        mixer.calls.clear()

        for (percent in 10..60 step 10) viewModel.onGainChanged(StemKind.VOCALS, percent)
        runCurrent()

        assertEquals(StemKind.VOCALS to 0.6f, mixer.calls.last())
        assertEquals(Triple("1", StemKind.VOCALS, 0.6f), stems.savedGains.last())
        assertEquals(60, observer.ready.stems.first { it.kind == StemKind.VOCALS }.percent)
    }

    @Test
    fun `a percent outside the range is clamped`() = runTest {
        val (viewModel, _) = opened()
        mixer.calls.clear()

        viewModel.onGainChanged(StemKind.VOCALS, 250)
        runCurrent()

        assertEquals(listOf(StemKind.VOCALS to 1f), mixer.calls)
    }

    @Test
    fun `in the full track mode a gain change does nothing`() = runTest {
        val (viewModel, _) = opened()
        viewModel.onModeSelected(TrackMode.FULL_TRACK)
        runCurrent()

        viewModel.onGainChanged(StemKind.VOCALS, 10)
        viewModel.onMuteToggled(StemKind.VOCALS)
        runCurrent()

        assertTrue(stems.savedGains.isEmpty())
    }

    @Test
    fun `mute saves a gain of zero and the row is muted`() = runTest {
        val (viewModel, observer) = opened()

        viewModel.onMuteToggled(StemKind.BASS)
        runCurrent()

        assertEquals(Triple("1", StemKind.BASS, 0f), stems.savedGains.last())
        val bass = observer.ready.stems.first { it.kind == StemKind.BASS }
        assertTrue(bass.isMuted)
        assertEquals(0, bass.percent)
    }

    @Test
    fun `unmute gives back the gain from before the mute`() = runTest {
        val (viewModel, observer) = opened()
        viewModel.onMuteToggled(StemKind.BASS)
        runCurrent()

        viewModel.onMuteToggled(StemKind.BASS)
        runCurrent()

        assertEquals(Triple("1", StemKind.BASS, 0.8f), stems.savedGains.last())
        assertFalse(observer.ready.stems.first { it.kind == StemKind.BASS }.isMuted)
    }

    @Test
    fun `unmute of a stem that was already silent goes to the full level`() = runTest {
        stems.add("1", TrackMode.STEMS, mapOf(StemKind.VOCALS to 0f, StemKind.DRUMS to 1f))
        val (viewModel, observer) = opened()
        assertTrue(observer.ready.stems.first { it.kind == StemKind.VOCALS }.isMuted)

        viewModel.onMuteToggled(StemKind.VOCALS)
        runCurrent()

        assertEquals(Triple("1", StemKind.VOCALS, 1f), stems.savedGains.last())
    }

    @Test
    fun `a stem dragged to zero is muted and a toggle unmutes it`() = runTest {
        val (viewModel, observer) = opened()
        viewModel.onGainChanged(StemKind.DRUMS, 0)
        runCurrent()
        assertTrue(observer.ready.stems.first { it.kind == StemKind.DRUMS }.isMuted)

        viewModel.onMuteToggled(StemKind.DRUMS)
        runCurrent()

        assertFalse(observer.ready.stems.first { it.kind == StemKind.DRUMS }.isMuted)
    }

    @Test
    fun `when another separated track opens then the panel shows its stems`() = runTest {
        val (_, observer) = opened()

        openForPractice(other)
        runCurrent()

        val ready = observer.ready
        assertEquals("2", ready.trackId)
        assertEquals(listOf(StemKind.VOCALS), ready.stems.map { it.kind })
        assertEquals(30, ready.stems.single().percent)
    }

    @Test
    fun `when a track that is not separated opens then the panel is unavailable`() = runTest {
        val (_, observer) = opened()

        openForPractice(plain)
        runCurrent()

        assertEquals(InstrumentsUiState.Unavailable, observer.state)
    }

    @Test
    fun `the gain before a mute is forgotten when the track changes`() = runTest {
        val (viewModel, _) = opened()
        viewModel.onMuteToggled(StemKind.VOCALS)
        runCurrent()
        openForPractice(other)
        runCurrent()
        openForPractice(song)
        runCurrent()

        // Track 1 is muted in the database, and what it was before is not remembered any more.
        viewModel.onMuteToggled(StemKind.VOCALS)
        runCurrent()

        assertEquals(Triple("1", StemKind.VOCALS, 1f), stems.savedGains.last())
    }

    @Test
    fun `a gain of the first track is not applied to the second`() = runTest {
        val (viewModel, observer) = opened()
        viewModel.onGainChanged(StemKind.VOCALS, 90)
        runCurrent()

        openForPractice(other)
        runCurrent()

        assertEquals(30, observer.ready.stems.single().percent)
        assertEquals(Triple("1", StemKind.VOCALS, 0.9f), stems.savedGains.last())
    }
}
