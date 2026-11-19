package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.data.stems.InMemoryModeSwitchRepository
import com.damianogiusti.slowdowner.domain.stems.ObserveModeSwitchUseCase
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.SwitchTrackModeUseCase
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.damianogiusti.slowdowner.domain.playback.ReadWaveformUseCase
import com.damianogiusti.slowdowner.domain.playback.SliderCurves
import com.damianogiusti.slowdowner.domain.player.ObservePlayerPreferencesUseCase
import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import com.damianogiusti.slowdowner.domain.player.SetLoopFineUseCase
import com.damianogiusti.slowdowner.domain.player.SetPitchFineUseCase
import com.damianogiusti.slowdowner.domain.practice.ApplyPresetUseCase
import com.damianogiusti.slowdowner.domain.practice.DeletePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.ObservePresetsUseCase
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeRepository
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlinx.coroutines.flow.flowOf
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.SetLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.ui.fake.FakeExportRepository
import com.damianogiusti.slowdowner.ui.fake.FakePlaybackEngine
import com.damianogiusti.slowdowner.ui.fake.FakePlayerPreferencesRepository
import com.damianogiusti.slowdowner.ui.fake.FakePracticeRepository
import com.damianogiusti.slowdowner.ui.fake.FakeStemsRepository
import com.damianogiusti.slowdowner.ui.fake.NoStemMixer
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.error_cannot_open
import com.damianogiusti.slowdowner.ui.resources.error_track_too_long
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.math.roundToInt
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val sources = FakeAudioSourceRepository(OpenResult.Opened)
    private val track = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val engine = FakePlaybackEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val preferences = FakePlayerPreferencesRepository()
    private val modeSwitch = InMemoryModeSwitchRepository()
    private val preset = PracticePreset(1, "1", "Slow", PracticeSettings(60f))
    private val practiceRepository = object : PracticeRepository by FakePracticeRepository() {
        override fun observePresets(trackId: String) = flowOf(if (trackId == "1") listOf(preset) else emptyList())
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when the track is too long then the message says it is too long`() = runTest {
        sources.result = OpenResult.TooLong

        assertEquals(listOf(UiMessage(Res.string.error_track_too_long, listOf("Song"))), selectTrack())
    }

    @Test
    fun `when the open fails then the message is the generic one`() = runTest {
        sources.result = OpenResult.Failed

        assertEquals(listOf(UiMessage(Res.string.error_cannot_open, listOf("Song"))), selectTrack())
    }

    @Test
    fun `when the track opens then there is no message`() = runTest {
        assertTrue(selectTrack().isEmpty())
    }

    @Test
    fun `when a track is decoding then the state says it is opening`() = runTest {
        sources.decode = CompletableDeferred()
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onTrackSelected(track)
        runCurrent()

        assertTrue(viewModel.state.value.isOpening)
    }

    @Test
    fun `when the decode ends then the state is no longer opening`() = runTest {
        val decode = CompletableDeferred<Unit>()
        sources.decode = decode
        val viewModel = createViewModel()
        runCurrent()
        viewModel.onTrackSelected(track)
        runCurrent()

        decode.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.isOpening)
    }

    @Test
    fun `when the open fails then the state is no longer opening`() = runTest {
        sources.result = OpenResult.Failed
        val decode = CompletableDeferred<Unit>()
        sources.decode = decode
        val viewModel = createViewModel()
        runCurrent()
        viewModel.onTrackSelected(track)
        runCurrent()

        decode.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.isOpening)
    }

    @Test
    fun `when two tracks are picked quickly then it stays opening until the second is open`() = runTest {
        val first = CompletableDeferred<Unit>()
        sources.decode = first
        val viewModel = createViewModel()
        runCurrent()
        viewModel.onTrackSelected(track)
        runCurrent()
        val second = CompletableDeferred<Unit>()
        sources.decode = second
        viewModel.onTrackSelected(track)
        runCurrent()

        first.complete(Unit)
        runCurrent()
        val isOpeningBetween = viewModel.state.value.isOpening
        second.complete(Unit)
        runCurrent()

        assertTrue(isOpeningBetween)
        assertFalse(viewModel.state.value.isOpening)
    }

    @Test
    fun `when Fine is off in the preference then the state says so`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        assertFalse(viewModel.state.value.controls.isPitchFine)
    }

    @Test
    fun `when the preference turns Fine on then the state follows`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        preferences.preferences.value = PlayerPreferences(isPitchFine = true)
        runCurrent()

        assertTrue(viewModel.state.value.controls.isPitchFine)
    }

    @Test
    fun `when Fine is toggled then the preference is saved`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onPitchFineToggled(true)
        runCurrent()

        assertTrue(preferences.preferences.value.isPitchFine)
        assertTrue(viewModel.state.value.controls.isPitchFine)
    }

    @Test
    fun `when Fine is off then the slider drives the engine curve`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onPitchChanged(7_000)

        assertEquals(listOf(7_000), engine.sliderPitchCalls)
    }

    @Test
    fun `when Fine is on then the slider sets five cent steps`() = runTest {
        preferences.preferences.value = PlayerPreferences(isPitchFine = true)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onPitchChanged(7_000)

        assertTrue(engine.sliderPitchCalls.isEmpty())
        assertEquals(SliderCurves.fineSemitones(7_000), engine.pitchSemitones)
        assertEquals(0, ((engine.pitchSemitones * 100).roundToInt()) % 5)
        assertEquals(engine.pitchSemitones, viewModel.state.value.controls.pitchSemitones)
    }

    @Test
    fun `when Fine is on then a step moves pitch by five cents`() = runTest {
        preferences.preferences.value = PlayerPreferences(isPitchFine = true)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onPitchStep(1)
        viewModel.onPitchStep(1)
        viewModel.onPitchStep(-1)

        assertEquals(0.05f, engine.pitchSemitones)
    }

    @Test
    fun `when Fine is off then a step moves pitch by a semitone`() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onPitchStep(1)

        assertEquals(1f, engine.pitchSemitones)
    }

    @Test
    fun `when pitch is reset then it goes back to zero and Fine stays on`() = runTest {
        preferences.preferences.value = PlayerPreferences(isPitchFine = true)
        val viewModel = createViewModel()
        runCurrent()
        viewModel.onPitchStep(3)

        viewModel.onPitchReset()

        assertEquals(0f, viewModel.state.value.controls.pitchSemitones)
        assertTrue(viewModel.state.value.controls.isPitchFine)
    }

    @Test
    fun `when the track changes then Fine stays on`() = runTest {
        preferences.preferences.value = PlayerPreferences(isPitchFine = true)
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onTrackSelected(track)
        runCurrent()
        viewModel.onTrackSelected(LibraryTrack("2", "Other", "/other.m4a", null, 0))
        runCurrent()

        assertTrue(viewModel.state.value.controls.isPitchFine)
    }

    @Test
    fun `when the waveform is off then the player has none`() = runTest {
        openFile()
        val viewModel = createViewModel()
        runCurrent()

        assertNull(viewModel.state.value.waveform)
    }

    @Test
    fun `when the waveform is on then it is scaled to the loudest bar`() = runTest {
        openFile()
        preferences.preferences.value = PlayerPreferences(isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        val levels = assertNotNull(viewModel.state.value.waveform).levels
        assertEquals(1f, levels.max())
        assertEquals(0.25f, levels.first())
    }

    @Test
    fun `when the engine cannot outline the file then there is no waveform`() = runTest {
        openFile()
        engine.outline = FloatArray(0)
        preferences.preferences.value = PlayerPreferences(isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        assertNull(viewModel.state.value.waveform)
    }

    @Test
    fun `when the waveform is turned off then it goes away`() = runTest {
        openFile()
        preferences.preferences.value = PlayerPreferences(isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        preferences.preferences.value = PlayerPreferences(isWaveformShown = false)
        runCurrent()

        assertNull(viewModel.state.value.waveform)
    }

    @Test
    fun `when loop Fine is off then there is no zoom`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        assertNull(viewModel.state.value.loopZoom)
    }

    @Test
    fun `when loop Fine is toggled then the preference is saved and the state follows`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.onLoopFineToggled(true)
        runCurrent()

        assertTrue(preferences.preferences.value.isLoopFine)
        assertTrue(viewModel.state.value.controls.isLoopFine)
        assertNotNull(viewModel.state.value.loopZoom)
    }

    @Test
    fun `with loop Fine the zoom covers the loop and some of each side`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true)
        val viewModel = createViewModel()
        runCurrent()

        val zoom = assertNotNull(viewModel.state.value.loopZoom)
        assertEquals(0.365f, zoom.from, 0.001f)
        assertEquals(0.535f, zoom.to, 0.001f)
    }

    @Test
    fun `without the waveform the zoom is a plain track and reads no peaks`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true)
        val viewModel = createViewModel()
        runCurrent()

        assertNull(assertNotNull(viewModel.state.value.loopZoom).levels)
        assertEquals(0, engine.peakReads)
    }

    @Test
    fun `with the waveform the zoom draws its levels`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true, isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        assertNotNull(assertNotNull(viewModel.state.value.loopZoom).levels)
    }

    @Test
    fun `without A and B there is nothing to zoom on`() = runTest {
        openFile()
        preferences.preferences.value = PlayerPreferences(isLoopFine = true)
        val viewModel = createViewModel()
        runCurrent()

        assertNull(viewModel.state.value.loopZoom)
    }

    @Test
    fun `a control that leaves the loop alone reuses the waveform and the zoom`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true, isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()
        val before = viewModel.state.value
        val reads = engine.peakReads

        viewModel.onSpeedReset()

        assertSame(before.waveform, viewModel.state.value.waveform)
        assertSame(before.loopZoom, viewModel.state.value.loopZoom)
        assertEquals(reads, engine.peakReads)
    }

    @Test
    fun `moving the loop reads only the new zoom`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true, isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()
        val waveform = viewModel.state.value.waveform
        val reads = engine.peakReads

        viewModel.onLoopRegionChanged(0.6f, 0.7f)

        assertSame(waveform, viewModel.state.value.waveform)
        assertEquals(0.565f, assertNotNull(viewModel.state.value.loopZoom).from, 0.001f)
        assertEquals(reads + 1, engine.peakReads)
    }

    @Test
    fun `when the waveform is turned off then the zoom stays as a plain track`() = runTest {
        openFile(LoopRegion(0.4f, 0.5f, isOn = true))
        preferences.preferences.value = PlayerPreferences(isLoopFine = true, isWaveformShown = true)
        val viewModel = createViewModel()
        runCurrent()

        preferences.preferences.value = PlayerPreferences(isLoopFine = true)
        runCurrent()

        assertNull(assertNotNull(viewModel.state.value.loopZoom).levels)
    }


    // A mode switch reopens the open track: now playing is null and the engine closed while it decodes.

    @Test
    fun `when the mode switches then the player keeps the track and its controls while the source decodes`() = runTest {
        val switching = startSwitch()

        val state = switching.viewModel.state.value
        assertNull(nowPlaying.observe().value)
        assertFalse(engine.isOpen)
        assertEquals(track, state.track)
        assertTrue(state.controls.isOpen)
        assertEquals(100f, state.controls.durationSeconds)
        assertTrue(state.isSwitchingMode)
        assertFalse(state.isOpening)
    }

    @Test
    fun `when the mode switches then the presets of the track stay`() = runTest {
        val switching = startSwitch()

        assertEquals(listOf(preset), switching.viewModel.state.value.presets)
    }

    @Test
    fun `when the mode switch ends then the player is the same and no longer switching`() = runTest {
        val switching = startSwitch()

        switching.finish()

        val state = switching.viewModel.state.value
        assertFalse(state.isSwitchingMode)
        assertEquals(track, state.track)
        assertTrue(state.controls.isOpen)
        assertEquals(track, nowPlaying.observe().value)
    }

    @Test
    fun `when the mode switch fails then the player goes to its empty state`() = runTest {
        val switching = startSwitch()
        sources.result = OpenResult.Failed

        switching.finish()

        val state = switching.viewModel.state.value
        assertFalse(state.isSwitchingMode)
        assertNull(state.track)
        assertFalse(state.controls.isOpen)
    }

    @Test
    fun `when the mode switches then export is not offered`() = runTest {
        val switching = startSwitch()

        assertFalse(switching.viewModel.state.value.canExport)
        switching.finish()
        assertTrue(switching.viewModel.state.value.canExport)
    }

    @Test
    fun `when a track is picked during a mode switch then it opens after the switch ends`() = runTest {
        val switching = startSwitch()
        val other = LibraryTrack("2", "Other", "/other.m4a", null, 0)

        switching.viewModel.onTrackSelected(other)
        runCurrent()
        assertNull(nowPlaying.observe().value)
        assertTrue(switching.viewModel.state.value.isOpening)

        switching.finish()

        assertEquals(other, nowPlaying.observe().value)
        assertFalse(switching.viewModel.state.value.isOpening)
    }

    private class Switching(
        val viewModel: PlayerViewModel,
        private val decode: CompletableDeferred<Unit>,
        private val scope: kotlinx.coroutines.test.TestScope,
    ) {
        fun finish() {
            decode.complete(Unit)
            scope.runCurrent()
        }
    }

    /** The track is open as the original and its mode switches to stems; the new source is still decoding. */
    private suspend fun kotlinx.coroutines.test.TestScope.startSwitch(): Switching {
        val stems = FakeStemsRepository().apply { add("1", TrackMode.FULL_TRACK, mapOf(StemKind.VOCALS to 1f)) }
        val closing = ClosingSources()
        val openForPractice = OpenTrackForPracticeUseCase(
            OpenTrackUseCase(closing, nowPlaying, stems, NoStemMixer, ConsoleLogger()),
            SavePracticeStateUseCase(engine, nowPlaying, practiceRepository),
            engine,
            practiceRepository,
        )
        val switch = SwitchTrackModeUseCase(stems, nowPlaying, closing, engine, openForPractice, modeSwitch)
        openForPractice(track)
        engine.durationSeconds = 100f
        val viewModel = createViewModel()
        runCurrent()

        val decode = CompletableDeferred<Unit>()
        sources.decode = decode
        backgroundScope.launch { switch(track.id, TrackMode.STEMS) }
        runCurrent()
        return Switching(viewModel, decode, this)
    }

    /** What a real engine does on an open: closed for the decode, open again when it succeeds. */
    private inner class ClosingSources : AudioSourceRepository by sources {
        override suspend fun open(source: AudioSource): OpenResult {
            engine.isOpen = false
            val result = sources.open(source)
            engine.isOpen = result == OpenResult.Opened
            return result
        }
    }

    private fun openFile(loop: LoopRegion? = null) {
        engine.isOpen = true
        engine.durationSeconds = 100f
        engine.outline = floatArrayOf(0.1f, 0.2f, 0.4f, 0.3f)
        loop?.let(engine::setLoop)
    }

    private fun kotlinx.coroutines.test.TestScope.selectTrack(): List<UiMessage> {
        val viewModel = createViewModel()
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        runCurrent()

        viewModel.onTrackSelected(track)
        runCurrent()

        return messages
    }

    private fun createViewModel(): PlayerViewModel {
        return PlayerViewModel(
            engine,
            OpenTrackForPracticeUseCase(
                OpenTrackUseCase(sources, nowPlaying, FakeStemsRepository(), NoStemMixer, ConsoleLogger()),
                SavePracticeStateUseCase(engine, nowPlaying, practiceRepository),
                engine,
                practiceRepository,
            ),
            ObserveNowPlayingUseCase(nowPlaying),
            ObserveModeSwitchUseCase(modeSwitch),
            PracticeUseCases(
                StepSpeedUseCase(engine),
                StepPitchUseCase(engine),
                ObservePlayerPreferencesUseCase(preferences),
                SetPitchFineUseCase(preferences),
                SetLoopFineUseCase(preferences),
                SetLoopRegionUseCase(engine),
                ObservePresetsUseCase(practiceRepository),
                SavePresetUseCase(engine, nowPlaying, practiceRepository),
                ApplyPresetUseCase(engine),
                DeletePresetUseCase(practiceRepository),
            ),
            ExportUseCases(
                ExportTrackUseCase(engine, nowPlaying, FakeExportRepository()),
                SaveExportUseCase(FakeExportRepository()),
            ),
            ReadWaveformUseCase(engine),
        )
    }
}
