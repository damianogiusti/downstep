package com.damianogiusti.slowdowner.domain.effects

class ApplyEffectsSettingsUseCase(private val engine: EffectsEngine) {
    operator fun invoke(settings: EffectsSettings) {
        engine.setEqEnabled(settings.eqEnabled)
        engine.setPreampDb(settings.preampDb)
        settings.bandGainsDb.forEachIndexed(engine::setBandGainDb)
        // Changing quality rebuilds the player: only do it when it really changes.
        if (engine.quality != settings.quality) engine.setQuality(settings.quality)
    }
}
