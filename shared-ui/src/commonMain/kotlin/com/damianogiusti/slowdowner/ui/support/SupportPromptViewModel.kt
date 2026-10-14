package com.damianogiusti.slowdowner.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.support.DismissSupportPromptUseCase
import com.damianogiusti.slowdowner.domain.support.ObserveSupportPromptUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The "Downstep is free" prompt. Desktop only: the Android build is not published. */
class SupportPromptViewModel(
    observeSupportPrompt: ObserveSupportPromptUseCase,
    private val dismissSupportPrompt: DismissSupportPromptUseCase,
) : ViewModel() {

    // A new launch count gives the same prompt: distinct, so the countdown does not restart.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SupportPromptUiState> = observeSupportPrompt()
        .distinctUntilChanged()
        .flatMapLatest { prompt -> prompt?.let { countdown(it.closeDelay) } ?: flowOf(SupportPromptUiState.Hidden) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportPromptUiState.Hidden)

    /** Close, "Why is it free?" and Donate all end the prompt until the next interval. */
    fun onDismissed() {
        viewModelScope.launch { dismissSupportPrompt() }
    }

    private fun countdown(closeDelay: Duration): Flow<SupportPromptUiState> = flow {
        var secondsLeft = closeDelay.inWholeSeconds.toInt()
        while (secondsLeft > 0) {
            emit(SupportPromptUiState.Visible(secondsLeft))
            delay(1.seconds)
            secondsLeft--
        }
        emit(SupportPromptUiState.Visible(0))
    }
}
