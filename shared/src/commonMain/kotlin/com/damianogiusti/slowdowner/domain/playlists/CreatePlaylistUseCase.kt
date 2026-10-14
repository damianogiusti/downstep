package com.damianogiusti.slowdowner.domain.playlists

class CreatePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(name: String): PlaylistResult {
        val validName = PlaylistName.normalize(name) ?: return PlaylistResult.InvalidName
        return PlaylistResult.Done(repository.create(validName))
    }
}
