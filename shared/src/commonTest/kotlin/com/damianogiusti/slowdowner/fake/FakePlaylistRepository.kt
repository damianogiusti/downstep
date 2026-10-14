package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.playlists.PlaylistRepository
import com.damianogiusti.slowdowner.domain.playlists.PlaylistWithTracks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakePlaylistRepository : PlaylistRepository {

    val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private var nextId = 1L

    override fun observePlaylists(): Flow<List<Playlist>> = playlists

    override fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        playlists.map { all -> all.firstOrNull { it.id == playlistId }?.let { PlaylistWithTracks(it, emptyList()) } }

    override fun observePlaylistIdsContaining(trackId: String): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun create(name: String): Long {
        val id = nextId++
        playlists.update { it + Playlist(id, name, 0) }
        return id
    }

    override suspend fun rename(playlistId: Long, name: String) = playlists.update { all ->
        all.map { if (it.id == playlistId) it.copy(name = name) else it }
    }

    override suspend fun delete(playlistId: Long) = playlists.update { all -> all.filterNot { it.id == playlistId } }
    override suspend fun addTrack(playlistId: Long, trackId: String) = playlists.value.any { it.id == playlistId }
    override suspend fun removeTrack(playlistId: Long, trackId: String) = Unit
    override suspend fun reorder(playlistId: Long, trackIds: List<String>) = Unit
}
