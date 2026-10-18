package com.damianogiusti.slowdowner.domain.effects

/** At start: the quality and the global EQ. A track's own EQ comes when it opens ([LoadTrackEqUseCase]). */
class RestoreEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
) {
    suspend operator fun invoke() = apply(repository.current())
}
