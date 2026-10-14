package com.damianogiusti.slowdowner.domain.playlists

class ReorderPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, trackIds: List<String>) = repository.reorder(playlistId, trackIds)
}
