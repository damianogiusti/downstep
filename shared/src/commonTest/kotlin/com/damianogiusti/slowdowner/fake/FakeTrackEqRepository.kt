package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.TrackEqRepository

/** Each track's own EQ in memory. Only tracks in [library] keep one, like the database. */
class FakeTrackEqRepository(var library: Set<String> = emptySet()) : TrackEqRepository {
    val curves = mutableMapOf<String, EqCurve>()
    /** Track ids in save order. */
    val saves = mutableListOf<String>()

    override suspend fun trackEq(trackId: String): EqCurve? = curves[trackId]

    override suspend fun saveTrackEq(trackId: String, eq: EqCurve?): Boolean {
        if (trackId !in library) return false
        saves += trackId
        if (eq == null) curves.remove(trackId) else curves[trackId] = eq
        return true
    }
}
