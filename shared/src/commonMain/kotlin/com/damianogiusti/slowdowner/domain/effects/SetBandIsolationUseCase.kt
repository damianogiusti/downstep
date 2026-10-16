package com.damianogiusti.slowdowner.domain.effects

/**
 * Turns Isolate on, changes it or turns it off ([IsolationBands.FULL_MIX] gives the user's own EQ
 * back). Only the engine changes: the overlay is never saved into [EffectsSettings].
 */
class SetBandIsolationUseCase(private val apply: ApplyEffectsSettingsUseCase) {
    /** [settings] are the user's own, as last edited. */
    operator fun invoke(settings: EffectsSettings, isolation: IsolationBands) = apply(settings, isolation)
}
