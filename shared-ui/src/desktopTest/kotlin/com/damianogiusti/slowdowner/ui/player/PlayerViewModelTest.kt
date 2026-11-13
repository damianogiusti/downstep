package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.SliderCurves
import com.damianogiusti.slowdowner.domain.player.ObservePlayerPreferencesUseCase
import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
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
import com.damianogiusti.slowdowner.ui.fake.FakePlaybackEngine
import com.damianogiusti.slowdowner.ui.fake.FakePlayerPreferencesRepository
import com.damianogiusti.slowdowner.ui.fake.FakePracticeRepository
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.error_cannot_open
import com.damianogiusti.slowdowner.ui.resources.error_track_too_long
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
import kotlin.math.roundToInt
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val sources = FakeAudioSourceRepository(OpenResult.Opened)
    private val track = LibraryTrack("1", "Song", "/song.m4a", null, 0)
    private val engine = FakePlaybackEngine()
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val preferences = FakePlayerPreferencesRepository()

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
        )
    }
}
