package com.damianogiusti.slowdowner.domain.effects

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * The user's EQ edits: each one reaches the engine at once, the disk only once they settle. A save
 * ends in a preferences flush (plist, registry): done per slider step, it held back the next step.
 */
class UpdateEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
) {
    /**
     * Collects [settings] until they complete or the caller is cancelled. [isolation] is read at each
     * change and laid over it for the engine only. The last change is always saved, also when the
     * caller is cancelled before the pause (the screen went away mid-drag).
     */
    @OptIn(FlowPreview::class)
    suspend operator fun invoke(
        settings: Flow<EffectsSettings>,
        isolation: () -> IsolationBands = { IsolationBands.FULL_MIX },
    ) {
        // Each written by one coroutine only, and read once both have finished.
        var applied: EffectsSettings? = null
        var saved: EffectsSettings? = null
        try {
            settings
                .onEach {
                    apply(it, isolation())
                    applied = it
                }
                .debounce(SAVE_DEBOUNCE_MS)
                .collect {
                    repository.save(it)
                    saved = it
                }
        } finally {
            val last = applied
            if (last != null && last != saved) withContext(NonCancellable) { repository.save(last) }
        }
    }

    companion object {
        /** As for the practice state (AutoSavePracticeStateUseCase). */
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
