package com.damianogiusti.slowdowner.ui.whatsnew

sealed interface WhatsNewUiState {
    data object Hidden : WhatsNewUiState

    /** [sections]: newest first. */
    data class Visible(val sections: List<WhatsNewSection>) : WhatsNewUiState
}

data class WhatsNewSection(val version: String, val lines: List<String>)
