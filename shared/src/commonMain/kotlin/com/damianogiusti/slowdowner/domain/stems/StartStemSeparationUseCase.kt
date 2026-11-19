package com.damianogiusti.slowdowner.domain.stems

class StartStemSeparationUseCase(private val repository: StemSeparationRepository) {
    suspend operator fun invoke(trackId: String): SeparationStart = repository.start(trackId)
}
