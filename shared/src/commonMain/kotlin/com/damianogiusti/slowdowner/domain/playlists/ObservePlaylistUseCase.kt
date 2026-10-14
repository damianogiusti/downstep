package com.damianogiusti.slowdowner.domain.playlists

import kotlinx.coroutines.flow.Flow

class ObservePlaylistUseCase(private val repository: PlaylistRepository) {
    operator fun invoke(playlistId: Long): Flow<PlaylistWithTracks?> = repository.observePlaylist(playlistId)
}
