package com.damianogiusti.slowdowner.ui.support

sealed interface SupportPromptUiState {
    data object Hidden : SupportPromptUiState

    /** Close, Esc and a click outside work once [closeSecondsLeft] reaches zero. */
    data class Visible(val closeSecondsLeft: Int) : SupportPromptUiState {
        val canClose: Boolean get() = closeSecondsLeft == 0
    }
}
