package com.damianogiusti.slowdowner.domain.stems

class AcknowledgeStemSeparationUseCase(private val repository: StemSeparationRepository) {
    suspend operator fun invoke() = repository.acknowledge()
}
