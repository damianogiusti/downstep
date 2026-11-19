package com.damianogiusti.slowdowner.domain.stems

class CancelStemSeparationUseCase(private val repository: StemSeparationRepository) {
    suspend operator fun invoke() = repository.cancel()
}
