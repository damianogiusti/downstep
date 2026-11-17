package com.damianogiusti.slowdowner.domain.player

/** Player choices of the app, the same for every song: not part of a track's practice settings. */
data class PlayerPreferences(
    /** Pitch moves in steps of 5 cents instead of whole semitones. */
    val isPitchFine: Boolean = false,
    /** The loop card shows a zoom on the loop, to place A and B precisely. */
    val isLoopFine: Boolean = false,
    /** The timeline draws the track's waveform. Chosen in Settings. */
    val isWaveformShown: Boolean = false,
)
