package com.damianogiusti.slowdowner.domain.player

class SetLoopFineUseCase(private val repository: PlayerPreferencesRepository) {
    suspend operator fun invoke(isOn: Boolean) = repository.save(repository.current().copy(isLoopFine = isOn))
}
