package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultStemsRepository(
    private val store: StemsStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    logger: Logger,
) : StemsRepository {

    private val mapper = TrackStemsMapper(logger)

    override fun observe(trackId: String): Flow<TrackStems?> =
        store.observe(trackId).map { it?.let(mapper::toDomain) }.flowOn(dispatcher)

    override fun observeSeparatedTrackIds(): Flow<Set<String>> =
        store.observeSeparatedTrackIds().map { it.toSet() }.flowOn(dispatcher)

    override suspend fun setMode(trackId: String, mode: TrackMode) =
        withContext(dispatcher) { store.setMode(trackId, mode.name) }

    override suspend fun setGain(trackId: String, stem: StemKind, gain: Float) {
        // NaN compares false with everything: it would be stored and break the mix.
        if (gain.isNaN()) return
        withContext(dispatcher) { store.setGain(trackId, stem.name, gain.coerceIn(0f, 1f)) }
    }

    override suspend fun delete(trackId: String) = withContext(dispatcher) { store.delete(trackId) }
}
