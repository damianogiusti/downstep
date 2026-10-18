package com.damianogiusti.slowdowner.domain.theme

import kotlinx.coroutines.flow.Flow

class ObserveThemeUseCase(private val repository: ThemeRepository) {
    operator fun invoke(): Flow<ThemeSelection> = repository.observe()
}
