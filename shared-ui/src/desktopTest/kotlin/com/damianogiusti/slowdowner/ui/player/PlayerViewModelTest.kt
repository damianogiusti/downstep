package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.export.CreateTrackFromLoopUseCase
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.RenderRange
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.export.SuggestLoopTrackTitleUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
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
import com.damianogiusti.slowdowner.domain.practice.SavePracticeStateUseCase
import com.damianogiusti.slowdowner.domain.practice.SavePresetUseCase
import com.damianogiusti.slowdowner.domain.practice.SetLoopRegionUseCase
import com.damianogiusti.slowdowner.domain.practice.StepPitchUseCase
import com.damianogiusti.slowdowner.domain.practice.StepSpeedUseCase
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.fake.FakeAudioSourceRepository
import com.damianogiusti.slowdowner.ui.fake.FakeExportRepository
import com.damianogiusti.slowdowner.ui.fake.FakeLibraryRepository
import com.damianogiusti.slowdowner.ui.fake.FakePlaybackEngine
import com.damianogiusti.slowdowner.ui.fake.FakePlayerPreferencesRepository
import com.damianogiusti.slowdowner.ui.fake.FakePracticeRepository
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
    private val exports = FakeExportRepository()

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

    @Test
    fun `with the loop on a new track from the loop asks for a title`() = runTest {
        val viewModel = openWithLoop()

        viewModel.onNewTrackFromLoopClicked()

        assertEquals(ExportUiState.NamingLoopTrack("Song (loop 0:10–0:20)"), viewModel.state.value.export)
    }

    @Test
    fun `with the loop off no title is asked`() = runTest {
        val viewModel = openWithLoop()
        viewModel.onLoopToggled(false)

        viewModel.onNewTrackFromLoopClicked()

        assertEquals(ExportUiState.Idle, viewModel.state.value.export)
        assertFalse(viewModel.state.value.canExportLoop)
    }

    @Test
    fun `when the title is confirmed then the track is created and offered`() = runTest {
        val viewModel = openWithLoop()
        val created = mutableListOf<LibraryTrack>()
        backgroundScope.launch { viewModel.loopTracks.collect { created += it } }
        runCurrent()
        viewModel.onNewTrackFromLoopClicked()

        viewModel.onLoopTrackTitleConfirmed("Solo")
        runCurrent()

        assertEquals(RenderRange(0.1f, 0.2f), exports.originalRange)
        assertEquals(listOf("Solo"), created.map { it.title })
        assertEquals(ExportUiState.Idle, viewModel.state.value.export)
    }

    @Test
    fun `while the track renders the controls are frozen`() = runTest {
        exports.gate = CompletableDeferred()
        val viewModel = openWithLoop()
        viewModel.onNewTrackFromLoopClicked()

        viewModel.onLoopTrackTitleConfirmed("Solo")
        runCurrent()

        assertEquals(ExportUiState.Running(0f, ExportTarget.LIBRARY), viewModel.state.value.export)
        assertTrue(viewModel.state.value.isExporting)
    }

    @Test
    fun `when naming is dismissed then nothing is rendered`() = runTest {
        val viewModel = openWithLoop()
        viewModel.onNewTrackFromLoopClicked()

        viewModel.onLoopTrackNamingDismissed()
        runCurrent()

        assertEquals(ExportUiState.Idle, viewModel.state.value.export)
        assertNull(exports.originalRange)
    }

    @Test
    fun `exporting the loop renders only the loop`() = runTest {
        val viewModel = openWithLoop()

        viewModel.onExportLoopClicked()
        runCurrent()

        assertEquals(RenderRange(0.1f, 0.2f), exports.exportedRange)
        assertTrue(viewModel.state.value.export is ExportUiState.Ready)
    }

    @Test
    fun `exporting the whole track ignores the loop`() = runTest {
        val viewModel = openWithLoop()

        viewModel.onExportClicked()
        runCurrent()

        assertEquals(RenderRange.WHOLE_FILE, exports.exportedRange)
    }

    /** "Song" open, 100 s, with the loop on 10..20 s. */
    private fun kotlinx.coroutines.test.TestScope.openWithLoop(): PlayerViewModel {
        nowPlaying.set(track)
        engine.isOpen = true
        engine.durationSeconds = 100f
        engine.setLoopOn(true)
        engine.loopStart = 0.1f
        engine.loopEnd = 0.2f
        val viewModel = createViewModel()
        runCurrent()
        return viewModel
    }

    private fun createViewModel(): PlayerViewModel {
        val practiceRepository = FakePracticeRepository()
        return PlayerViewModel(
            engine,
            OpenTrackForPracticeUseCase(
                OpenTrackUseCase(sources, nowPlaying),
                SavePracticeStateUseCase(engine, nowPlaying, practiceRepository),
                engine,
                practiceRepository,
            ),
            ObserveNowPlayingUseCase(nowPlaying),
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
                ExportTrackUseCase(engine, nowPlaying, exports),
                SaveExportUseCase(exports),
                SuggestLoopTrackTitleUseCase(engine, nowPlaying),
                CreateTrackFromLoopUseCase(engine, nowPlaying, exports, FakeLibraryRepository(), practiceRepository),
            ),
            ReadWaveformUseCase(engine),
        )
    }
}
