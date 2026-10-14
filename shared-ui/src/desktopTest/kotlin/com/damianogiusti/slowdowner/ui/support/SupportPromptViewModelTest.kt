package com.damianogiusti.slowdowner.ui.support

import com.damianogiusti.slowdowner.domain.support.DismissSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import com.damianogiusti.slowdowner.domain.support.SupportPromptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun `when prompt is not due then state is Hidden`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 2)
        val viewModel = subscribedViewModel()

        assertEquals(SupportPromptUiState.Hidden, viewModel.state.value)
    }

    @Test
    fun `when prompt is due the first time then close counts down from five`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = subscribedViewModel()
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
        val viewModel = subscribedViewModel()
        advanceTimeBy(2.seconds)
        runCurrent()

        repository.history.update { it.copy(launchCount = 4) }
        runCurrent()

        assertEquals(SupportPromptUiState.Visible(closeSecondsLeft = 3), viewModel.state.value)
    }

    @Test
    fun `when dismissed then state is Hidden and shown time is saved`() = runTest {
        repository.history.value = SupportPromptHistory(launchCount = 3)
        val viewModel = subscribedViewModel()

        viewModel.onDismissed()
        runCurrent()

        assertEquals(SupportPromptUiState.Hidden, viewModel.state.value)
        assertEquals(clock.now(), repository.history.value.lastShownAt)
    }

    private fun TestScope.subscribedViewModel(): SupportPromptViewModel {
        val viewModel = SupportPromptViewModel(
            ObserveSupportPromptUseCase(repository, clock),
            DismissSupportPromptUseCase(repository, clock),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private class FakeRepository : SupportPromptRepository {
        val history = MutableStateFlow(SupportPromptHistory())
        override fun observe() = history
        override suspend fun recordLaunch() = history.update { it.copy(launchCount = it.launchCount + 1) }
        override suspend fun recordShown(at: Instant) = history.update { it.copy(lastShownAt = at) }
    }
}
