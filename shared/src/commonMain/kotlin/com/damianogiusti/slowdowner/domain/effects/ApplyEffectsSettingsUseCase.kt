package com.damianogiusti.slowdowner.domain.effects

class ApplyEffectsSettingsUseCase(private val engine: EffectsEngine) {
    /** [isolation] is laid over [settings] on the way to the engine; [settings] stay as they are. */
    operator fun invoke(settings: EffectsSettings, isolation: IsolationBands = IsolationBands.FULL_MIX) {
        val played = isolation.applyTo(settings)
        engine.setEqEnabled(played.eqEnabled)
        engine.setPreampDb(played.preampDb)
        played.bandGainsDb.forEachIndexed(engine::setBandGainDb)
        // Changing quality rebuilds the player: only do it when it really changes.
        if (engine.quality != played.quality) engine.setQuality(played.quality)
    }
}
