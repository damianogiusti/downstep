package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistDao
import com.damianogiusti.slowdowner.data.playlists.PlaylistEntity
import com.damianogiusti.slowdowner.data.playlists.PlaylistSummaryRow
import com.damianogiusti.slowdowner.data.playlists.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [PlaylistDao]. Inherits appendTrack and reorder from the interface, so those
 * default methods are what the tests exercise.
 */
class FakePlaylistDao(tracks: List<TrackEntity> = emptyList()) : PlaylistDao {

    val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val links = MutableStateFlow<List<PlaylistTrackEntity>>(emptyList())
    private val tracks = MutableStateFlow(tracks)
    private var nextId = 1L

    override fun observePlaylists(): Flow<List<PlaylistSummaryRow>> = combine(playlists, links) { all, links ->
        all.sortedByDescending { it.updatedAt }.map { p -> PlaylistSummaryRow(p.id, p.name, links.count { it.playlistId == p.id }) }
    }

    override fun observePlaylist(playlistId: Long): Flow<PlaylistSummaryRow?> =
        observePlaylists().map { rows -> rows.firstOrNull { it.id == playlistId } }

    override fun observeTracksOf(playlistId: Long): Flow<List<TrackEntity>> = combine(links, tracks) { links, tracks ->
        links.filter { it.playlistId == playlistId }
            .sortedBy { it.position }
            .mapNotNull { link -> tracks.firstOrNull { it.id == link.trackId } }
    }

    override fun observePlaylistIdsContaining(trackId: String): Flow<List<Long>> =
        links.map { all -> all.filter { it.trackId == trackId }.map { it.playlistId } }

    override suspend fun insert(playlist: PlaylistEntity): Long {
        val id = nextId++
        playlists.update { it + playlist.copy(id = id) }
        return id
    }

    override suspend fun rename(playlistId: Long, name: String, now: Long) = playlists.update { all ->
        all.map { if (it.id == playlistId) it.copy(name = name, updatedAt = now) else it }
    }

    override suspend fun delete(playlistId: Long) {
        playlists.update { all -> all.filterNot { it.id == playlistId } }
        links.update { all -> all.filterNot { it.playlistId == playlistId } }
    }

    override suspend fun playlistExists(playlistId: Long) = playlists.value.any { it.id == playlistId }

    override suspend fun trackExists(trackId: String) = tracks.value.any { it.id == trackId }

    override suspend fun maxPosition(playlistId: Long) =
        links.value.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: -1

    override suspend fun insertTrack(link: PlaylistTrackEntity): Long {
        if (links.value.any { it.playlistId == link.playlistId && it.trackId == link.trackId }) return -1
        links.update { it + link }
        return 1
    }

    override suspend fun removeTrack(playlistId: Long, trackId: String) = links.update { all ->
        all.filterNot { it.playlistId == playlistId && it.trackId == trackId }
    }

    override suspend fun setPosition(playlistId: Long, trackId: String, position: Int) = links.update { all ->
        all.map { if (it.playlistId == playlistId && it.trackId == trackId) it.copy(position = position) else it }
    }

    override suspend fun touch(playlistId: Long, now: Long) = playlists.update { all ->
        all.map { if (it.id == playlistId) it.copy(updatedAt = now) else it }
    }
}
