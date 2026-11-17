package com.damianogiusti.slowdowner.domain.player

import kotlinx.coroutines.flow.Flow

class ObservePlayerPreferencesUseCase(private val repository: PlayerPreferencesRepository) {
    operator fun invoke(): Flow<PlayerPreferences> = repository.observe()
}
