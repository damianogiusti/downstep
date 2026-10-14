package com.damianogiusti.slowdowner.domain.effects

import kotlinx.coroutines.flow.Flow

class ObserveEffectsSettingsUseCase(private val repository: EffectsSettingsRepository) {
    operator fun invoke(): Flow<EffectsSettings> = repository.observe()
}
