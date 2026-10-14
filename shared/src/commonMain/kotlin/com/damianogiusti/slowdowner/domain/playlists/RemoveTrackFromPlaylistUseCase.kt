package com.damianogiusti.slowdowner.domain.playlists

class RemoveTrackFromPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, trackId: String) = repository.removeTrack(playlistId, trackId)
}
