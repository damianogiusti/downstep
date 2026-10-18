package com.damianogiusti.slowdowner.domain.effects

class ApplyEffectsSettingsUseCase(private val engine: EffectsEngine) {
    operator fun invoke(settings: EffectsSettings) {
        applyCurve(settings.eq)
        // Changing quality rebuilds the player: only do it when it really changes.
        if (engine.quality != settings.quality) engine.setQuality(settings.quality)
    }

    /** The EQ alone: what a track switch or a preset changes. Isolate is a stage of its own. */
    fun applyCurve(curve: EqCurve) {
        val played = curve.sanitised()
        engine.setEqBands(played.bands)
        engine.setPreampDb(played.preampDb)
        engine.setEqEnabled(played.enabled)
    }
}
