package com.damianogiusti.slowdowner.domain.player

class SetPitchFineUseCase(private val repository: PlayerPreferencesRepository) {
    suspend operator fun invoke(isOn: Boolean) = repository.save(repository.current().copy(isPitchFine = isOn))
}
