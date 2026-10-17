package com.damianogiusti.slowdowner.domain.theme

class SetThemeModeUseCase(private val repository: ThemeRepository) {
    suspend operator fun invoke(mode: ThemeMode) = repository.save(mode)
}
