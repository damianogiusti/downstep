package com.damianogiusti.slowdowner.domain.stems

/**
 * The one stem separation the app may run at a time. It lives with the app, not with a screen: a screen that
 * opens later sees the job that is running, or the outcome of the last one.
 */
sealed interface SeparationJobState {
    data object Idle : SeparationJobState

    /** [progress] is 0..1, in steps of one percent. */
    data class Running(val trackId: String, val progress: Float) : SeparationJobState

    /** Kept until the next [StemSeparationRepository.start] or [StemSeparationRepository.acknowledge]. */
    data class Finished(val trackId: String, val result: SeparationResult) : SeparationJobState
}

sealed interface SeparationResult {
    /** The stems are saved and the track counts as separated. */
    data object Separated : SeparationResult

    data class Failed(val reason: SeparationFailureReason) : SeparationResult

    data object Cancelled : SeparationResult
}

enum class SeparationFailureReason {
    /** The track cannot be read, has no audio, or has more than two channels. */
    UNREADABLE_TRACK,

    /** The track does not fit in the memory a separation may use. */
    TOO_LONG,

    /** The model is damaged or the runtime that runs it failed. */
    MODEL_ERROR,

    /** A stem could not be saved: the disk is full or the folder is gone. */
    WRITE_ERROR,

    /** The track was removed from the library while it was being separated. Nothing was kept. */
    TRACK_REMOVED,

    /** Something this version did not expect. It is reported as an issue. */
    UNEXPECTED,
}
