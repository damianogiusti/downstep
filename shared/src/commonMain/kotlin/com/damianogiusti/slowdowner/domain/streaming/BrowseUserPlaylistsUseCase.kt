package com.damianogiusti.slowdowner.domain.streaming

class BrowseUserPlaylistsUseCase(private val repository: StreamingCatalogRepository) {
    suspend operator fun invoke(): List<StreamingPlaylist> = repository.getUserPlaylists()
}
