package com.damianogiusti.slowdowner.domain.streaming

interface StreamingCatalogRepository {
    suspend fun getUserPlaylists(): List<StreamingPlaylist>
    suspend fun getPlaylistTracks(ownerId: String, playlistId: String): List<StreamingTrack>
    suspend fun getSavedTracks(): List<StreamingTrack>
}
