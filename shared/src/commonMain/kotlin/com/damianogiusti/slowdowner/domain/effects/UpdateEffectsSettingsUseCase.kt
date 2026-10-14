package com.damianogiusti.slowdowner.domain.effects

class UpdateEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
) {
    suspend operator fun invoke(settings: EffectsSettings) {
        apply(settings)
        repository.save(settings)
    }
}
