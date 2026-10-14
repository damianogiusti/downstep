package com.damianogiusti.slowdowner.domain.playlists

import kotlinx.coroutines.flow.Flow

class ObservePlaylistMembershipUseCase(private val repository: PlaylistRepository) {
    operator fun invoke(trackId: String): Flow<Set<Long>> = repository.observePlaylistIdsContaining(trackId)
}
