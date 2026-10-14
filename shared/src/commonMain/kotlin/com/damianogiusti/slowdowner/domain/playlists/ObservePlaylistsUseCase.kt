package com.damianogiusti.slowdowner.domain.playlists

import kotlinx.coroutines.flow.Flow

class ObservePlaylistsUseCase(private val repository: PlaylistRepository) {
    operator fun invoke(): Flow<List<Playlist>> = repository.observePlaylists()
}
