package com.damianogiusti.slowdowner.domain.playlists

/** False when the playlist or the track no longer exists. */
class AddTrackToPlaylistUseCase(private val repository: PlaylistRepository) {
    suspend operator fun invoke(playlistId: Long, trackId: String) = repository.addTrack(playlistId, trackId)
}
