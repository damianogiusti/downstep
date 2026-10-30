package com.damianogiusti.slowdowner.ui.update

import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.InstallUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ObserveAvailableUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.ReportUpdatePromptShownUseCase
import com.damianogiusti.slowdowner.domain.update.RestartIntoUpdateUseCase
import com.damianogiusti.slowdowner.domain.update.UpdateCheckResult
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import com.damianogiusti.slowdowner.domain.update.UpdateRepository
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    private val updates = FakeUpdateRepository()
    private val support = FakeSupportPromptRepository()
    private val logger = RecordingLogger()
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochSeconds(1_800_000_000)
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
    fun `when no newer version then state is None`() = runTest {
        updates.latest.value = release("1.0.3")
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(UpdateUiState.None, viewModel.state.value)
    }

    @Test
    fun `when a newer version is found then the dialog opens and it is logged once`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        updates.latest.value = release("1.0.4")
        runCurrent()

        assertEquals(
            UpdateUiState.Available(
                version = "1.0.4",
                installedVersion = "1.0.3",
                pageUrl = PAGE,
                canInstall = true,
                notes = emptyList(),
                isDialogOpen = true,
                install = UpdateInstallState.Idle,
            ),
            viewModel.state.value,
        )
        assertEquals(listOf("Update prompt shown"), logger.infos)
    }

    @Test
    fun `when the release lists its changes then the dialog shows them`() = runTest {
        updates.latest.value = release("1.0.4").copy(notes = listOf("Normalize volume."))
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(listOf("Normalize volume."), available(viewModel).notes)
    }

    @Test
    fun `when the support prompt is due then the dialog waits for it to close`() = runTest {
        support.history.value = SupportPromptHistory(launchCount = 3)
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        assertEquals(false, available(viewModel).isDialogOpen)
        assertEquals(emptyList(), logger.infos)

        support.history.update { it.copy(lastShownAt = clock.now()) }
        runCurrent()

        assertEquals(true, available(viewModel).isDialogOpen)
    }

    @Test
    fun `when later is clicked then the dialog closes and the banner opens it again`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onLaterClicked()
        runCurrent()
        assertEquals(false, available(viewModel).isDialogOpen)

        viewModel.onBannerClicked()
        runCurrent()
        assertEquals(true, available(viewModel).isDialogOpen)
        assertEquals(1, logger.infos.count { it == "Update prompt shown" })
    }

    @Test
    fun `when install is clicked then progress shows and the dialog waits for restart when ready`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onInstallClicked()
        runCurrent()
        assertEquals(UpdateInstallState.Downloading(40), available(viewModel).install)

        updates.installDone.complete(UpdateInstallResult.ReadyToRestart)
        runCurrent()
        assertEquals(UpdateInstallState.ReadyToRestart, available(viewModel).install)
        // Nothing swaps before the user has read that the app reopens by itself.
        assertEquals(0, updates.swapsStarted)
    }

    @Test
    fun `when restart is clicked then the swap starts and only then the app restarts`() = runTest {
        val viewModel = readyToRestart()

        viewModel.onRestartClicked()
        runCurrent()

        assertEquals(1, updates.swapsStarted)
        assertEquals(UpdateInstallState.Restarting, available(viewModel).install)
    }

    @Test
    fun `when the swap cannot start then the app does not quit and the dialog says so`() = runTest {
        updates.canStartSwap = false
        val viewModel = readyToRestart()

        viewModel.onRestartClicked()
        runCurrent()

        assertEquals(UpdateInstallState.Failed, available(viewModel).install)
    }

    @Test
    fun `when restart is clicked twice then one swap starts`() = runTest {
        val viewModel = readyToRestart()

        viewModel.onRestartClicked()
        viewModel.onRestartClicked()
        runCurrent()

        assertEquals(1, updates.swapsStarted)
    }

    @Test
    fun `when later is clicked while the swap starts then the dialog stays and the app restarts`() = runTest {
        val viewModel = readyToRestart()
        val swap = CompletableDeferred<Boolean>().also { updates.swapDone = it }

        viewModel.onRestartClicked()
        runCurrent()
        viewModel.onLaterClicked()
        swap.complete(true)
        runCurrent()

        assertEquals(true, available(viewModel).isDialogOpen)
        assertEquals(UpdateInstallState.Restarting, available(viewModel).install)
    }

    @Test
    fun `when a newer release appears then the ready update is not offered any more`() = runTest {
        val viewModel = readyToRestart()

        updates.latest.value = release("1.0.5")
        runCurrent()

        assertEquals("1.0.5", available(viewModel).version)
        assertEquals(UpdateInstallState.Idle, available(viewModel).install)
    }

    @Test
    fun `when the download reports progress after cancel then the dialog stays idle`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onInstallClicked()
        runCurrent()

        viewModel.onCancelClicked()
        // A read already in flight on the IO thread finishes after the cancel.
        updates.lastProgress?.invoke(1f)
        runCurrent()

        assertEquals(UpdateInstallState.Idle, available(viewModel).install)
    }

    @Test
    fun `when later is clicked on a ready update then it stays ready for the banner`() = runTest {
        val viewModel = readyToRestart()

        viewModel.onLaterClicked()
        runCurrent()
        assertEquals(false, available(viewModel).isDialogOpen)

        viewModel.onBannerClicked()
        runCurrent()
        assertEquals(true, available(viewModel).isDialogOpen)
        assertEquals(UpdateInstallState.ReadyToRestart, available(viewModel).install)
    }

    @Test
    fun `when downloading then later does not close the dialog`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onInstallClicked()
        runCurrent()

        viewModel.onLaterClicked()
        runCurrent()

        assertEquals(true, available(viewModel).isDialogOpen)
    }

    @Test
    fun `when the download is cancelled then the dialog is back to the start`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onInstallClicked()
        runCurrent()

        viewModel.onCancelClicked()
        runCurrent()

        assertEquals(UpdateInstallState.Idle, available(viewModel).install)
        assertEquals(true, updates.wasInstallCancelled)
    }

    @Test
    fun `when install fails then the dialog says so`() = runTest {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onInstallClicked()
        updates.installDone.complete(UpdateInstallResult.Failure)
        runCurrent()

        assertEquals(UpdateInstallState.Failed, available(viewModel).install)
    }

    @Test
    fun `when the download page is opened then the dialog closes, the banner stays`() = runTest {
        updates.latest.value = release("1.0.4", canInstall = false)
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onDownloadPageOpened()
        runCurrent()

        assertEquals(false, available(viewModel).isDialogOpen)
        assertEquals(false, available(viewModel).canInstall)
    }

    private fun available(viewModel: UpdateViewModel) = assertIs<UpdateUiState.Available>(viewModel.state.value)

    private fun TestScope.readyToRestart(): UpdateViewModel {
        updates.latest.value = release("1.0.4")
        val viewModel = viewModel()
        subscribe(viewModel)
        viewModel.onInstallClicked()
        updates.installDone.complete(UpdateInstallResult.ReadyToRestart)
        runCurrent()
        assertEquals(UpdateInstallState.ReadyToRestart, available(viewModel).install)
        return viewModel
    }

    private fun viewModel() = UpdateViewModel(
        ObserveAvailableUpdateUseCase(updates),
        ObserveSupportPromptUseCase(support, clock),
        InstallUpdateUseCase(updates, logger),
        RestartIntoUpdateUseCase(updates),
        ReportUpdatePromptShownUseCase(logger),
    )

    private fun TestScope.subscribe(viewModel: UpdateViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun release(version: String, canInstall: Boolean = true) =
        AppRelease(requireNotNull(AppVersion.parse(version)), PAGE, canInstall)

    private class FakeUpdateRepository : UpdateRepository {
        val latest = MutableStateFlow<AppRelease?>(null)
        val installDone = CompletableDeferred<UpdateInstallResult>()
        var wasInstallCancelled = false
        var canStartSwap = true
        var swapsStarted = 0
        var swapDone: CompletableDeferred<Boolean>? = null
        var lastProgress: ((Float) -> Unit)? = null

        override fun installedVersion() = AppVersion.parse("1.0.3")
        override fun observeLatestRelease() = latest
        override suspend fun refresh() = UpdateCheckResult.Done
        override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult {
            lastProgress = onProgress
            onProgress(0.4f)
            try {
                return installDone.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                wasInstallCancelled = true
                throw e
            }
        }

        override suspend fun startSwap(): Boolean {
            if (canStartSwap) swapsStarted++
            return swapDone?.await() ?: canStartSwap
        }
    }

    private class FakeSupportPromptRepository : SupportPromptRepository {
        val history = MutableStateFlow(SupportPromptHistory(launchCount = 1))
        override fun observe() = history
        override suspend fun recordLaunch() = history.update { it.copy(launchCount = it.launchCount + 1) }
        override suspend fun recordShown(at: Instant) = history.update { it.copy(lastShownAt = at) }
    }

    private class RecordingLogger : Logger {
        val infos = mutableListOf<String>()
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) {
            infos += message
        }
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
    }

    private companion object {
        const val PAGE = "https://github.com/damianogiusti/downstep/releases/tag/v1.0.4"
    }
}
