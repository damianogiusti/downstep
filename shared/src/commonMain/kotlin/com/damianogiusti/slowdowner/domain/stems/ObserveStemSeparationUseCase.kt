package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

class ObserveStemSeparationUseCase(private val repository: StemSeparationRepository) {
    operator fun invoke(): Flow<SeparationJobState> = repository.observe()
}
