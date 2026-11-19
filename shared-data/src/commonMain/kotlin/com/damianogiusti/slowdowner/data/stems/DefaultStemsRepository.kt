package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.PlayableStems
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DefaultStemsRepository(
    private val store: StemsStore,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
    private val logger: Logger,
) : StemsRepository {

    private val mapper = TrackStemsMapper(logger)
    private val hasLoggedMissing = AtomicBoolean(false)

    override fun observe(trackId: String): Flow<TrackStems?> =
        store.observe(trackId).map { it?.let(mapper::toDomain) }.flowOn(dispatcher)

    override fun observeSeparatedTrackIds(): Flow<Set<String>> =
        store.observeSeparatedTrackIds().map { it.toSet() }.flowOn(dispatcher)

    override suspend fun playableStems(trackId: String): PlayableStems? = withContext(dispatcher) {
        val dto = store.observe(trackId).first() ?: return@withContext null
        val playable = mapper.toPlayable(dto, store.dirFor(trackId)) ?: return@withContext null
        if (playable.stems.any { !File(it.path).isFile }) {
            // Cleared by the user or dropped by a sync tool: not a bug, but seen once is enough.
            if (hasLoggedMissing.compareAndSet(false, true)) logger.w("Stem files are missing, the track plays as the original")
            return@withContext null
        }
        playable
    }

    override suspend fun setMode(trackId: String, mode: TrackMode) =
        withContext(dispatcher) { store.setMode(trackId, mode.name) }

    override suspend fun setGain(trackId: String, stem: StemKind, gain: Float) {
        // NaN compares false with everything: it would be stored and break the mix.
        if (gain.isNaN()) return
        withContext(dispatcher) { store.setGain(trackId, stem.name, gain.coerceIn(0f, 1f)) }
    }

    override suspend fun setMuted(trackId: String, stem: StemKind, isMuted: Boolean) =
        withContext(dispatcher) { store.setMuted(trackId, stem.name, isMuted) }

    override suspend fun setSoloed(trackId: String, stem: StemKind, isSoloed: Boolean) =
        withContext(dispatcher) { store.setSoloed(trackId, stem.name, isSoloed) }

    override suspend fun delete(trackId: String) = withContext(dispatcher) { store.delete(trackId) }
}
