package com.damianogiusti.slowdowner.domain.streaming

class BrowsePlaylistTracksUseCase(private val repository: StreamingCatalogRepository) {
    suspend operator fun invoke(ownerId: String, playlistId: String): List<StreamingTrack> =
        repository.getPlaylistTracks(ownerId, playlistId)
}
