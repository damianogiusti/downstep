package com.damianogiusti.slowdowner.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.support.DismissSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ReportSupportPromptShownUseCase
import com.damianogiusti.slowdowner.domain.support.ReportWhyFreeOpenedUseCase
import com.damianogiusti.slowdowner.domain.support.SupportPrompt
import com.damianogiusti.slowdowner.domain.support.SupportPromptAction
import com.damianogiusti.slowdowner.domain.whatsnew.IsWhatsNewDueUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** The "Downstep is free" prompt. Desktop only: the Android build is not published. */
class SupportPromptViewModel(
    observeSupportPrompt: ObserveSupportPromptUseCase,
    private val reportSupportPromptShown: ReportSupportPromptShownUseCase,
    private val reportWhyFreeOpened: ReportWhyFreeOpenedUseCase,
    private val dismissSupportPrompt: DismissSupportPromptUseCase,
    isWhatsNewDue: IsWhatsNewDueUseCase,
) : ViewModel() {

    // Once per process: a new subscription (the window back from the Dock) is not a new prompt.
    private var isShownReported = false

    // A new launch count gives the same prompt: distinct, so the countdown does not restart.
    @OptIn(ExperimentalCoroutinesApi::class)
    // One popup per launch: while What's new is due, the prompt waits for the next launch. Nothing shows
    // until that is known.
    val state: StateFlow<SupportPromptUiState> = combine(
        observeSupportPrompt().distinctUntilChanged(),
        flow { emit(isWhatsNewDue()) },
    ) { prompt, isWhatsNewDue -> prompt.takeUnless { isWhatsNewDue } }
        .flatMapLatest { prompt -> prompt?.let(::countdown) ?: flowOf(SupportPromptUiState.Hidden) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportPromptUiState.Hidden)

    /** The prompt stays open: back from the browser, people can still donate or close it. */
    fun onWhyFreeClicked() {
        val duringCountdown = isCountingDown()
        viewModelScope.launch { reportWhyFreeOpened(duringCountdown) }
    }

    fun onDonateClicked() = dismiss(SupportPromptAction.DONATE)

    fun onCloseClicked() = dismiss(SupportPromptAction.CLOSE)

    /** Esc or a click outside: the dialog only asks once the countdown is over. */
    fun onDismissRequested() = dismiss(SupportPromptAction.DISMISS)

    private fun isCountingDown() = (state.value as? SupportPromptUiState.Visible)?.canClose == false

    private fun dismiss(action: SupportPromptAction) {
        val duringCountdown = isCountingDown()
        viewModelScope.launch { dismissSupportPrompt(action, duringCountdown) }
    }

    private fun countdown(prompt: SupportPrompt): Flow<SupportPromptUiState> = flow {
        if (!isShownReported) {
            isShownReported = true
            reportSupportPromptShown(prompt)
        }
        var secondsLeft = prompt.closeDelay.inWholeSeconds.toInt()
        while (secondsLeft > 0) {
            emit(SupportPromptUiState.Visible(secondsLeft))
            delay(1.seconds)
            secondsLeft--
        }
        emit(SupportPromptUiState.Visible(0))
    }
}
