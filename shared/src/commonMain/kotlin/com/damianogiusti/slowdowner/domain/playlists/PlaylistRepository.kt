package com.damianogiusti.slowdowner.domain.playlists

import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    /** Most recently changed first. */
    fun observePlaylists(): Flow<List<Playlist>>
    /** Emits null once the playlist is deleted. */
    fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?>
    fun observePlaylistIdsContaining(trackId: String): Flow<Set<Long>>
    suspend fun create(name: String): Long
    suspend fun rename(playlistId: Long, name: String)
    suspend fun delete(playlistId: Long)
    /**
     * Appends at the end; does nothing if the track is already there. False when the
     * playlist or the track no longer exists.
     */
    suspend fun addTrack(playlistId: Long, trackId: String): Boolean
    suspend fun removeTrack(playlistId: Long, trackId: String)
    suspend fun reorder(playlistId: Long, trackIds: List<String>)
}
