package com.damianogiusti.slowdowner.domain.effects

/**
 * The effects kept in the preferences: the global EQ, which every track without its own starts with
 * (and the EQ of a track outside the library), and the time-stretch quality. A track's own EQ is
 * saved with the track ([TrackEqRepository]); the L/R mix stays per session on purpose.
 */
data class EffectsSettings(
    val eq: EqCurve = EqCurve.FLAT,
    val quality: ProcessingQuality = ProcessingQuality.HIGH,
)
