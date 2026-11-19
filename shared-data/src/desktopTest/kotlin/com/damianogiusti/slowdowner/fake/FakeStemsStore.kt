package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.stems.StemFileDto
import com.damianogiusti.slowdowner.data.stems.StemsStore
import com.damianogiusti.slowdowner.data.stems.TrackStemsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

/** In-memory [StemsStore] that keeps what the repository asks it to write, as plain text like the real one. */
class FakeStemsStore(initial: List<TrackStemsDto> = emptyList()) : StemsStore {

    val tracks = MutableStateFlow(initial.associateBy { it.trackId })
    val deleted = mutableListOf<String>()

    override fun observe(trackId: String): Flow<TrackStemsDto?> = tracks.map { it[trackId] }

    override fun observeSeparatedTrackIds(): Flow<List<String>> = tracks.map { it.keys.toList() }

    override suspend fun setMode(trackId: String, mode: String) =
        tracks.update { all -> all[trackId]?.let { all + (trackId to it.copy(mode = mode)) } ?: all }

    override suspend fun setGain(trackId: String, stem: String, gain: Float) = tracks.update { all ->
        val current = all[trackId] ?: return@update all
        all + (trackId to current.copy(stems = current.stems.map { if (it.stem == stem) it.copy(gain = gain) else it }))
    }

    override suspend fun add(stems: TrackStemsDto) = tracks.update { it + (stems.trackId to stems) }

    override suspend fun delete(trackId: String) {
        deleted += trackId
        tracks.update { it - trackId }
    }

    override fun dirFor(trackId: String): File = error("unused")

    companion object {
        fun stem(name: String, gain: Float = 1f) = StemFileDto(name, "${name.lowercase()}.wav", gain)
    }
}
