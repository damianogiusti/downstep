package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.export.ExportTrackUseCase
import com.damianogiusti.slowdowner.domain.export.SaveExportUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val sources = FakeAudioSourceRepository(OpenResult.Opened)
    private val track = LibraryTrack("1", "Song", "/song.m4a", null, 0)

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

    private fun kotlinx.coroutines.test.TestScope.selectTrack(): List<UiMessage> {
        val engine = FakePlaybackEngine()
        val nowPlaying = InMemoryNowPlayingRepository()
        val practiceRepository = FakePracticeRepository()
        val viewModel = PlayerViewModel(
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
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch { viewModel.messages.collect { messages += it } }
        runCurrent()

        viewModel.onTrackSelected(track)
        runCurrent()

        return messages
    }
}
