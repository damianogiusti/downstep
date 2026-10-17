package com.damianogiusti.slowdowner.domain.theme

import kotlinx.coroutines.flow.Flow

class ObserveThemeModeUseCase(private val repository: ThemeRepository) {
    operator fun invoke(): Flow<ThemeMode> = repository.observe()
}
