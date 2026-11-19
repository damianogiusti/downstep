package com.damianogiusti.slowdowner.domain.stems

class SetTrackModeUseCase(private val repository: StemsRepository) {
    suspend operator fun invoke(trackId: String, mode: TrackMode) = repository.setMode(trackId, mode)
}
