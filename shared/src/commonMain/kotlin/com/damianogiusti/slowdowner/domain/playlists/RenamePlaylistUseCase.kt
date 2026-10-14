package com.damianogiusti.slowdowner.domain.playlists

class RenamePlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, name: String): PlaylistResult {
        val validName = PlaylistName.normalize(name) ?: return PlaylistResult.InvalidName
        repository.rename(playlistId, validName)
        return PlaylistResult.Done(playlistId)
    }
}
