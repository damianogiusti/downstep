package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.Flow

class ObserveStemModelUseCase(private val repository: StemModelRepository) {
    operator fun invoke(): Flow<StemModelState> = repository.observe()
}
