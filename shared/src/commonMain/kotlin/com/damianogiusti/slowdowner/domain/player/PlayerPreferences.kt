package com.damianogiusti.slowdowner.domain.player

/** Player choices of the app, the same for every song: not part of a track's practice settings. */
data class PlayerPreferences(
    /** Pitch moves in steps of 5 cents instead of whole semitones. */
    val isPitchFine: Boolean = false,
)
