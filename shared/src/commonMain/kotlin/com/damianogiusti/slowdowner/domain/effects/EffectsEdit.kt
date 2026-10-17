package com.damianogiusti.slowdowner.domain.effects

/**
 * The effects as they should sound, and where they belong: the curve to the track it was made on
 * ([trackId]), or to the global EQ when no track is open (null); the quality always to the
 * preferences. A [isLoad] (another track's EQ coming in) is played but not saved: it already is.
 */
data class EffectsEdit(
    val trackId: String?,
    val settings: EffectsSettings,
    val isLoad: Boolean = false,
)
