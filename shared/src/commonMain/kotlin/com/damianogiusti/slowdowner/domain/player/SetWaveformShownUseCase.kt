package com.damianogiusti.slowdowner.domain.player

class SetWaveformShownUseCase(private val repository: PlayerPreferencesRepository) {
    suspend operator fun invoke(isShown: Boolean) = repository.save(repository.current().copy(isWaveformShown = isShown))
}
