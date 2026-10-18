package com.damianogiusti.slowdowner.domain.theme

class SetThemeUseCase(private val repository: ThemeRepository) {
    suspend operator fun invoke(selection: ThemeSelection) = repository.save(selection)
}
