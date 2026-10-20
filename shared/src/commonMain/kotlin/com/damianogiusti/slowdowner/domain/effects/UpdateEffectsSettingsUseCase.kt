package com.damianogiusti.slowdowner.domain.effects

import com.damianogiusti.slowdowner.logging.Logger

class UpdateEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(settings: EffectsSettings) {
        val wasVolumeNormalized = repository.current().isVolumeNormalized
        apply(settings)
        repository.save(settings)
        // A choice the user makes once in a while, unlike the sliders: it tells whether the feature is used.
        if (settings.isVolumeNormalized != wasVolumeNormalized) {
            logger.i("Volume normalization changed", mapOf("enabled" to settings.isVolumeNormalized.toString()))
        }
    }
}
