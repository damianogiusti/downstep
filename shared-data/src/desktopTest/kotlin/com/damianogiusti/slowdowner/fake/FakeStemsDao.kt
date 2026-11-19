package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.stems.StemsDao
import com.damianogiusti.slowdowner.data.stems.TrackStemEntity
import com.damianogiusti.slowdowner.data.stems.TrackStemStateEntity
import com.damianogiusti.slowdowner.data.stems.TrackStemsRelation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [StemsDao]: the cascade from state to stems is done by hand, as the foreign key does. */
class FakeStemsDao : StemsDao {

    val states = MutableStateFlow<List<TrackStemStateEntity>>(emptyList())
    val stems = MutableStateFlow<List<TrackStemEntity>>(emptyList())

    override fun observe(trackId: String): Flow<TrackStemsRelation?> = states.map { all ->
        all.firstOrNull { it.trackId == trackId }?.let { state ->
            TrackStemsRelation(state, stems.value.filter { it.trackId == trackId })
        }
    }

    override fun observeSeparatedTrackIds(): Flow<List<String>> = states.map { all -> all.map { it.trackId } }

    override suspend fun findStems(trackId: String) = stems.value.filter { it.trackId == trackId }

    override suspend fun updateMode(trackId: String, mode: String) =
        states.update { all -> all.map { if (it.trackId == trackId) it.copy(mode = mode) else it } }

    override suspend fun updateGain(trackId: String, stem: String, gain: Float) =
        stems.update { all -> all.map { if (it.trackId == trackId && it.stem == stem) it.copy(gain = gain) else it } }

    override suspend fun updateMuted(trackId: String, stem: String, isMuted: Boolean) =
        stems.update { all -> all.map { if (it.trackId == trackId && it.stem == stem) it.copy(isMuted = isMuted) else it } }

    override suspend fun updateSoloed(trackId: String, stem: String, isSoloed: Boolean) =
        stems.update { all -> all.map { if (it.trackId == trackId && it.stem == stem) it.copy(isSoloed = isSoloed) else it } }

    override suspend fun delete(trackId: String) {
        states.update { all -> all.filterNot { it.trackId == trackId } }
        stems.update { all -> all.filterNot { it.trackId == trackId } }
    }

    override suspend fun insertState(state: TrackStemStateEntity) = states.update { it + state }

    override suspend fun insertStems(stems: List<TrackStemEntity>) = this.stems.update { it + stems }

    override suspend fun replace(state: TrackStemStateEntity, stems: List<TrackStemEntity>) {
        delete(state.trackId)
        insertState(state)
        insertStems(stems)
    }
}
