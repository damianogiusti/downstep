package com.damianogiusti.slowdowner.domain.stems

/** Whether the ML model that separates stems is on this device. */
sealed interface StemModelState {
    data object NotDownloaded : StemModelState
    data object Ready : StemModelState
}
