package com.damianogiusti.slowdowner.domain.stems

class DeleteTrackStemsUseCase(private val repository: StemsRepository) {
    suspend operator fun invoke(trackId: String) = repository.delete(trackId)
}
