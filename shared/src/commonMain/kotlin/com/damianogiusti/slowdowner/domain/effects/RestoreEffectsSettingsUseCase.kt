package com.damianogiusti.slowdowner.domain.effects

class RestoreEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
) {
    suspend operator fun invoke() = apply(repository.current())
}
