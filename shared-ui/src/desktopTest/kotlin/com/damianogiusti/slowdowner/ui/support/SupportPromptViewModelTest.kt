package com.damianogiusti.slowdowner.ui.support

import com.damianogiusti.slowdowner.domain.support.DismissSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ReportSupportPromptShownUseCase
import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SupportPromptViewModelTest {

    private val repository = FakeRepository()
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
    fun `when prompt is not due then state is Hidden and nothing is logged`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 2)
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(SupportPromptUiState.Hidden, viewModel.state.value)
        assertEquals(emptyList(), logger.infos)
    }

    @Test
    fun `when prompt is due the first time then close counts down from five`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = viewModel()
        subscribe(viewModel)
        assertEquals(SupportPromptUiState.Visible(closeSecondsLeft = 5), viewModel.state.value)

        advanceTimeBy(4.seconds)
        runCurrent()
        assertEquals(SupportPromptUiState.Visible(closeSecondsLeft = 1), viewModel.state.value)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(SupportPromptUiState.Visible(closeSecondsLeft = 0), viewModel.state.value)
    }

    @Test
    fun `when a launch is counted then the countdown does not restart`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceTimeBy(2.seconds)
        runCurrent()

        repository.history.update { it.copy(launchCount = 4) }
        runCurrent()

        assertEquals(SupportPromptUiState.Visible(closeSecondsLeft = 3), viewModel.state.value)
    }

    @Test
    fun `when shown then it is logged once, even after a new subscription`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = viewModel()
        subscribe(viewModel).cancel()
        advanceTimeBy(6.seconds)
        subscribe(viewModel)

        assertEquals(listOf("Support prompt shown" to mapOf("occurrence" to "first")), logger.infos)
    }

    @Test
    fun `when why free is clicked during the countdown then prompt hides and the action is logged`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onWhyFreeClicked()
        runCurrent()

        assertEquals(SupportPromptUiState.Hidden, viewModel.state.value)
        assertEquals(clock.now(), repository.history.value.lastShownAt)
        assertEquals(
            "Support prompt closed" to mapOf("occurrence" to "first", "action" to "why_free", "duringCountdown" to "true"),
            logger.infos.last(),
        )
    }

    @Test
    fun `when dismissed after the countdown then the action is logged`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = viewModel()
        subscribe(viewModel)
        advanceTimeBy(5.seconds)
        runCurrent()

        viewModel.onDismissRequested()
        runCurrent()

        assertEquals(
            mapOf("occurrence" to "first", "action" to "dismiss", "duringCountdown" to "false"),
            logger.infos.last().second,
        )
    }

    private fun viewModel() = SupportPromptViewModel(
        ObserveSupportPromptUseCase(repository, clock),
        ReportSupportPromptShownUseCase(logger),
        DismissSupportPromptUseCase(repository, logger, clock),
    )

    private fun TestScope.subscribe(viewModel: SupportPromptViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private class FakeRepository : SupportPromptRepository {
        val history = MutableStateFlow(SupportPromptHistory())
        override fun observe() = history
        override suspend fun recordLaunch() = history.update { it.copy(launchCount = it.launchCount + 1) }
        override suspend fun recordShown(at: Instant) = history.update { it.copy(lastShownAt = at) }
    }

    private class RecordingLogger : Logger {
        val infos = mutableListOf<Pair<String, Map<String, String>>>()
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) {
            infos += message to properties
        }
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
    }
}
