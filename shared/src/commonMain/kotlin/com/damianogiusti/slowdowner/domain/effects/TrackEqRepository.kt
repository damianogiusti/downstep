package com.damianogiusti.slowdowner.domain.effects

/** Each library track's own EQ. */
interface TrackEqRepository {
    /** The track's own curve, or null when it has none and uses the global one. */
    suspend fun trackEq(trackId: String): EqCurve?

    /**
     * Saves the track's own curve; null goes back to the global one. False when nothing was saved:
     * the track is not in the library (opened from elsewhere, or deleted meanwhile).
     */
    suspend fun saveTrackEq(trackId: String, eq: EqCurve?): Boolean
}
