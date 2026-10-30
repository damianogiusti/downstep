package com.damianogiusti.slowdowner.domain.effects

import com.damianogiusti.slowdowner.logging.Logger
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
    private val logger: Logger,
) {
    /**
     * Collects [settings] until they complete or the caller is cancelled. The last change is always
     * saved, also when the caller is cancelled before the pause (the screen went away mid-drag).
     * [onSaved] hears of each save, so a caller can tell what a quit still has to write with [saveNow].
     */
    @OptIn(FlowPreview::class)
    suspend operator fun invoke(
        settings: Flow<EffectsSettings>,
        onSaved: (EffectsSettings) -> Unit = {},
    ) {
        // Each written by one coroutine only, and read once both have finished.
        var wasVolumeNormalized = repository.current().isVolumeNormalized
        var applied: EffectsSettings? = null
        var saved: EffectsSettings? = null
        try {
            settings
                .onEach {
                    apply(it)
                    applied = it
                    // A choice the user makes once in a while, unlike the sliders: it tells whether the feature is used.
                    if (it.isVolumeNormalized != wasVolumeNormalized) {
                        wasVolumeNormalized = it.isVolumeNormalized
                        logger.i("Volume normalization changed", mapOf("enabled" to it.isVolumeNormalized.toString()))
                    }
                }
                .debounce(SAVE_DEBOUNCE_MS)
                .collect {
                    repository.save(it)
                    saved = it
                    onSaved(it)
                }
        } finally {
            val last = applied
            if (last != null && last != saved) withContext(NonCancellable) {
                repository.save(last)
                onSaved(last)
            }
        }
    }

    /**
     * Saves [settings] at once. For quitting: the process ends before the pause, and before a
     * cancelled collector could save (nothing cancels it on the way out).
     */
    suspend fun saveNow(settings: EffectsSettings) = repository.save(settings)

    companion object {
        /** As for the practice state (AutoSavePracticeStateUseCase). */
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
