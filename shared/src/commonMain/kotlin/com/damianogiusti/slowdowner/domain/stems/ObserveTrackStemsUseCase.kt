package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

class ObserveTrackStemsUseCase(private val repository: StemsRepository) {
    operator fun invoke(trackId: String): Flow<TrackStems?> = repository.observe(trackId)
}
