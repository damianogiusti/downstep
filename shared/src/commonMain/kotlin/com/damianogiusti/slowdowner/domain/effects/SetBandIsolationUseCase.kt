package com.damianogiusti.slowdowner.domain.effects

/**
 * Turns Isolate on, changes it or turns it off ([IsolationBands.FULL_MIX]). Only the engine's
 * isolation stage changes: the user's EQ is untouched, and nothing is saved.
 */
class SetBandIsolationUseCase(private val engine: EffectsEngine) {
    operator fun invoke(isolation: IsolationBands) = engine.setIsolation(isolation.mask)
}
