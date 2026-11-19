package com.damianogiusti.slowdowner.domain.stems

class SetStemGainUseCase(private val repository: StemsRepository) {
    suspend operator fun invoke(trackId: String, stem: StemKind, gain: Float) =
        repository.setGain(trackId, stem, gain)
}
