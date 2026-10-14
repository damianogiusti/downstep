package com.damianogiusti.slowdowner.domain.playlists

class DeletePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long) = repository.delete(playlistId)
}
