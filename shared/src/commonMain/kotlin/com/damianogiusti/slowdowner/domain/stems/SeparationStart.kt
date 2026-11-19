package com.damianogiusti.slowdowner.domain.stems

/** What happened to a request to separate a track. Only [Started] begins a job. */
sealed interface SeparationStart {
    data object Started : SeparationStart

    /** Another track is being separated: one job at a time. */
    data object Busy : SeparationStart

    data object ModelMissing : SeparationStart

    data object TrackMissing : SeparationStart
}
