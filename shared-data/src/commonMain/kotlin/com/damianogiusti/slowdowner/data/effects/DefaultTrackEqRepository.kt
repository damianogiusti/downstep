package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.data.practice.PracticeDao
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.TrackEqRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** In track_practice_state.eq_curve, as [EqCurveCodec] JSON; NULL = the track uses the global EQ. */
class DefaultTrackEqRepository(
    private val dao: PracticeDao,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val now: () -> Long = System::currentTimeMillis,
) : TrackEqRepository {

    override suspend fun trackEq(trackId: String): EqCurve? = withContext(dispatcher) {
        EqCurveCodec.decode(dao.trackEqCurve(trackId))
    }

    override suspend fun saveTrackEq(trackId: String, eq: EqCurve?): Boolean = withContext(dispatcher) {
        dao.setTrackEqCurve(trackId, eq?.let(EqCurveCodec::encode), now())
        // No row: the track is not in the library (or was just deleted).
        dao.findState(trackId) != null
    }
}
