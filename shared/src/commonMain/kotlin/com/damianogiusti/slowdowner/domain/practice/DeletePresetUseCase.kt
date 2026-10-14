package com.damianogiusti.slowdowner.domain.practice

class DeletePresetUseCase(private val repository: PracticeRepository) {
    suspend operator fun invoke(preset: PracticePreset) = repository.deletePreset(preset.id)
}
