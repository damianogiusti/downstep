package com.damianogiusti.slowdowner.domain.practice

import kotlinx.coroutines.flow.Flow

class ObservePresetsUseCase(private val repository: PracticeRepository) {
    operator fun invoke(trackId: String): Flow<List<PracticePreset>> = repository.observePresets(trackId)
}
