package com.damianogiusti.slowdowner.ui.stems

import com.damianogiusti.slowdowner.domain.stems.AcknowledgeStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.CancelStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.DeleteTrackStemsUseCase
import com.damianogiusti.slowdowner.domain.stems.DownloadStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.ObserveSeparatedTracksUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemModelUseCase
import com.damianogiusti.slowdowner.domain.stems.ObserveStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.SeparationFailureReason
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StartStemSeparationUseCase
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.StemPlatform
import com.damianogiusti.slowdowner.ui.common.UiMessage
import com.damianogiusti.slowdowner.ui.fake.FakeStemModelRepository
import com.damianogiusti.slowdowner.ui.fake.FakeStemSeparationRepository
import com.damianogiusti.slowdowner.ui.fake.FakeStemsRepository
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.stems_busy
import com.damianogiusti.slowdowner.ui.resources.stems_download_error_corrupted
import com.damianogiusti.slowdowner.ui.resources.stems_download_error_generic
import com.damianogiusti.slowdowner.ui.resources.stems_download_error_network
import com.damianogiusti.slowdowner.ui.resources.stems_error_removed
import com.damianogiusti.slowdowner.ui.resources.stems_error_too_long
import com.damianogiusti.slowdowner.ui.resources.stems_ready
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StemSeparationViewModelTest {

    private val stems = FakeStemsRepository()
    private val model = FakeStemModelRepository()
    private val separation = FakeStemSeparationRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(isDeprecated: Boolean = false) = StemSeparationViewModel(
        StemUseCases(
            observeSeparatedTracks = ObserveSeparatedTracksUseCase(stems),
            observeModel = ObserveStemModelUseCase(model),
            observeSeparation = ObserveStemSeparationUseCase(separation),
            startSeparation = StartStemSeparationUseCase(separation),
            cancelSeparation = CancelStemSeparationUseCase(separation),
            acknowledgeSeparation = AcknowledgeStemSeparationUseCase(separation),
            downloadModel = DownloadStemModelUseCase(model),
            deleteTrackStems = DeleteTrackStemsUseCase(stems),
        ),
        StemPlatform(isDeprecated),
    )

    /** Keeps the state flow and the messages collected, as the app does. */
    private class Observer(viewModel: StemSeparationViewModel, scope: TestScope) {
        var state = StemSeparationUiState()
        val messages = mutableListOf<UiMessage>()
        val states = mutableListOf<StemSeparationUiState>()

        init {
            scope.backgroundScope.launch { viewModel.state.collect { state = it; states += it } }
            scope.backgroundScope.launch { viewModel.messages.collect { messages += it } }
        }
    }

    // runCurrent, not advanceUntilIdle: the collectors live in backgroundScope, which "idle" ignores.
    private fun TestScope.observe(viewModel: StemSeparationViewModel) = Observer(viewModel, this).also { runCurrent() }

    @Test
    fun `the separated ids reach the state`() = runTest {
        val observer = observe(viewModel())

        stems.separatedIds.value = setOf("a", "b")
        runCurrent()

        assertEquals(setOf("a", "b"), observer.state.separatedTrackIds)
    }

    @Test
    fun `with the model ready a separation starts at once and shows no dialog`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(listOf("track"), separation.started)
        assertEquals(StemDialog.None, observer.state.dialog)
        assertEquals(StemProgress.Separating(0), observer.state.progress)
    }

    @Test
    fun `progress is shown in whole percents`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        separation.state.value = SeparationJobState.Running("track", 0.29f)
        runCurrent()

        assertEquals(StemProgress.Separating(29), observer.state.progress)
    }

    @Test
    fun `when the model is missing the dialog opens and nothing starts`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(StemDialog.DownloadModel(isDeprecatedPlatform = false), observer.state.dialog)
        assertTrue(separation.started.isEmpty())
        assertEquals(0, model.downloads)
    }

    @Test
    fun `cancelling the model dialog downloads nothing and closes it`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onModelDownloadDismissed()
        runCurrent()

        assertEquals(StemDialog.None, observer.state.dialog)
        assertEquals(0, model.downloads)
        assertTrue(separation.started.isEmpty())
    }

    @Test
    fun `after the download the separation of the chosen track starts by itself`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onModelDownloadConfirmed()
        runCurrent()

        assertEquals(1, model.downloads)
        assertEquals(listOf("track"), separation.started)
        assertEquals(StemDialog.None, observer.state.dialog)
        assertEquals(StemProgress.Separating(0), observer.state.progress)
    }

    @Test
    fun `the download shows its progress in whole percents in the banner`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        model.progressSteps = listOf(0.071f, 0.5f)
        model.gate = CompletableDeferred()
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onModelDownloadConfirmed()
        runCurrent()

        assertEquals(StemProgress.DownloadingModel(50), observer.state.progress)
        assertTrue(StemProgress.DownloadingModel(7) in observer.states.map { it.progress })

        model.gate?.complete(Unit)
        runCurrent()
        assertEquals(StemProgress.Separating(0), observer.state.progress)
    }

    @Test
    fun `cancelling the download stops it and starts no separation`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        model.gate = CompletableDeferred()
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()
        viewModel.onModelDownloadConfirmed()
        runCurrent()

        viewModel.onCancelClicked()
        runCurrent()

        assertEquals(StemProgress.None, observer.state.progress)
        assertTrue(separation.started.isEmpty())
        assertEquals(0, separation.cancels)
        assertTrue(observer.messages.isEmpty())
    }

    @Test
    fun `a download that fails tells why and starts nothing`() = runTest {
        val cases = mapOf(
            ModelDownloadResult.NetworkError to Res.string.stems_download_error_network,
            ModelDownloadResult.Corrupted to Res.string.stems_download_error_corrupted,
            ModelDownloadResult.Failure(IllegalStateException("x")) to Res.string.stems_download_error_generic,
        )
        for ((result, text) in cases) {
            model.state.value = StemModelState.NotDownloaded
            model.downloadResult = result
            val viewModel = viewModel()
            val observer = observe(viewModel)
            viewModel.onSeparateClicked("track")
            runCurrent()

            viewModel.onModelDownloadConfirmed()
            runCurrent()

            assertEquals(listOf(UiMessage(text)), observer.messages, "for $result")
            assertEquals(StemProgress.None, observer.state.progress)
        }
        assertTrue(separation.started.isEmpty())
    }

    @Test
    fun `starting while a separation runs says so and starts nothing`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("first")
        runCurrent()

        viewModel.onSeparateClicked("second")
        runCurrent()

        assertEquals(listOf("first"), separation.started)
        assertEquals(listOf(UiMessage(Res.string.stems_busy)), observer.messages)
    }

    @Test
    fun `starting while the model downloads says so`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        model.gate = CompletableDeferred()
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("first")
        runCurrent()
        viewModel.onModelDownloadConfirmed()
        runCurrent()

        viewModel.onSeparateClicked("second")
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.stems_busy)), observer.messages)
        assertEquals(1, model.downloads)
    }

    @Test
    fun `when the repository answers Busy the message is the same`() = runTest {
        separation.startResult = SeparationStart.Busy
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.stems_busy)), observer.messages)
    }

    @Test
    fun `when the model went missing the dialog opens`() = runTest {
        separation.startResult = SeparationStart.ModelMissing
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(StemDialog.DownloadModel(isDeprecatedPlatform = false), observer.state.dialog)
    }

    @Test
    fun `when the track is gone the user is told`() = runTest {
        separation.startResult = SeparationStart.TrackMissing
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.stems_error_removed)), observer.messages)
    }

    @Test
    fun `a finished separation says so once and is acknowledged`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)

        separation.state.value = SeparationJobState.Finished("track", SeparationResult.Separated)
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.stems_ready)), observer.messages)
        assertEquals(1, separation.acknowledges)
        assertEquals(SeparationJobState.Idle, separation.state.value)
    }

    @Test
    fun `a failed separation gives the reason in words`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)

        separation.state.value = SeparationJobState.Finished("track", SeparationResult.Failed(SeparationFailureReason.TOO_LONG))
        runCurrent()

        assertEquals(listOf(UiMessage(Res.string.stems_error_too_long)), observer.messages)
        assertEquals(1, separation.acknowledges)
    }

    @Test
    fun `a cancelled separation shows nothing and is acknowledged`() = runTest {
        val viewModel = viewModel()
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onCancelClicked()
        runCurrent()

        assertEquals(1, separation.cancels)
        assertTrue(observer.messages.isEmpty())
        assertEquals(1, separation.acknowledges)
        assertEquals(StemProgress.None, observer.state.progress)
    }

    @Test
    fun `deleting the instruments asks the repository`() = runTest {
        stems.separatedIds.value = setOf("a", "b")
        val viewModel = viewModel()
        val observer = observe(viewModel)

        viewModel.onDeleteStemsConfirmed("a")
        runCurrent()

        assertEquals(listOf("a"), stems.deleted)
        assertEquals(setOf("b"), observer.state.separatedTrackIds)
    }

    @Test
    fun `on a deprecated platform the model dialog carries the notice`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        val viewModel = viewModel(isDeprecated = true)
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertEquals(StemDialog.DownloadModel(isDeprecatedPlatform = true), observer.state.dialog)
    }

    @Test
    fun `on a deprecated platform with the model ready the user confirms once`() = runTest {
        val viewModel = viewModel(isDeprecated = true)
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("first")
        runCurrent()
        assertEquals(StemDialog.ConfirmDeprecatedPlatform, observer.state.dialog)
        assertTrue(separation.started.isEmpty())

        viewModel.onDeprecationAccepted()
        runCurrent()
        assertEquals(listOf("first"), separation.started)
        assertEquals(StemDialog.None, observer.state.dialog)

        separation.state.value = SeparationJobState.Idle
        runCurrent()
        viewModel.onSeparateClicked("second")
        runCurrent()
        assertEquals(listOf("first", "second"), separation.started)
    }

    @Test
    fun `declining the deprecation notice starts nothing`() = runTest {
        val viewModel = viewModel(isDeprecated = true)
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onDeprecationDismissed()
        runCurrent()

        assertEquals(StemDialog.None, observer.state.dialog)
        assertTrue(separation.started.isEmpty())
    }

    @Test
    fun `after the download on a deprecated platform no second notice is asked`() = runTest {
        model.state.value = StemModelState.NotDownloaded
        val viewModel = viewModel(isDeprecated = true)
        val observer = observe(viewModel)
        viewModel.onSeparateClicked("track")
        runCurrent()

        viewModel.onModelDownloadConfirmed()
        runCurrent()

        assertEquals(listOf("track"), separation.started)
        assertEquals(StemDialog.None, observer.state.dialog)
    }

    @Test
    fun `on a normal platform no deprecation dialog ever shows`() = runTest {
        val viewModel = viewModel(isDeprecated = false)
        val observer = observe(viewModel)

        viewModel.onSeparateClicked("track")
        runCurrent()

        assertTrue(observer.states.none { it.dialog != StemDialog.None })
    }
}
