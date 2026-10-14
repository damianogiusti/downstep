package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.library.TrackDao
import com.damianogiusti.slowdowner.data.library.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [TrackDao] with the same conflict rules as the Room one. */
class FakeTrackDao(initial: List<TrackEntity> = emptyList()) : TrackDao {

    val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<TrackEntity>> = rows

    override suspend fun find(id: String) = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(track: TrackEntity) = rows.update { current -> current.filterNot { it.id == track.id } + track }

    override suspend fun insertIgnoringExisting(tracks: List<TrackEntity>) = rows.update { current ->
        current + tracks.filterNot { new -> current.any { it.id == new.id } }
    }

    override suspend fun delete(id: String) = rows.update { current -> current.filterNot { it.id == id } }
}
