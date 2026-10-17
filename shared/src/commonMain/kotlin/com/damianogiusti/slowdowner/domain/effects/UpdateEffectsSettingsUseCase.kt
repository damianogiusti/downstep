package com.damianogiusti.slowdowner.domain.effects

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * The user's EQ edits: each one reaches the engine at once, the disk only once they settle. A save
 * of the global EQ ends in a preferences flush (plist, registry): done per drag step, it held back
 * the next step.
 *
 * Each edit is saved where it was made ([EffectsEdit.trackId]): an edit still waiting for the pause
 * is saved at once when another track's EQ comes in (a load, or an edit for another track), so a
 * track switch inside the pause can never write one track's EQ onto the next.
 */
class UpdateEffectsSettingsUseCase(
    private val apply: ApplyEffectsSettingsUseCase,
    private val repository: EffectsSettingsRepository,
    private val trackEq: TrackEqRepository,
) {
    /**
     * Collects [edits] until they complete or the caller is cancelled. Loads are played, not saved.
     * The last edit is always saved, also when the caller is cancelled before the pause (the screen
     * went away mid-drag). [onSaved] hears of each save, so a caller can tell what a quit still has
     * to write with [saveNow].
     */
    @OptIn(FlowPreview::class)
    suspend operator fun invoke(
        edits: Flow<EffectsEdit>,
        onSaved: (EffectsEdit) -> Unit = {},
    ) {
        // Played, not saved yet. Written by one coroutine at a time, and read once both have finished.
        var pending: EffectsEdit? = null
        suspend fun savePending() {
            val edit = pending ?: return
            pending = null
            save(edit)
            onSaved(edit)
        }
        try {
            edits
                .onEach { edit ->
                    apply(edit.settings)
                    val waiting = pending
                    if (waiting != null && (edit.isLoad || edit.trackId != waiting.trackId)) savePending()
                    if (!edit.isLoad) pending = edit
                }
                .filter { !it.isLoad }
                .debounce(SAVE_DEBOUNCE_MS)
                // Only if nothing saved it meanwhile: identity, an equal later edit is another one.
                .collect { if (it === pending) savePending() }
        } finally {
            if (pending != null) withContext(NonCancellable) { savePending() }
        }
    }

    /**
     * Saves [edit] at once. For quitting: the process ends before the pause, and before a cancelled
     * collector could save (nothing cancels it on the way out).
     */
    suspend fun saveNow(edit: EffectsEdit) = save(edit)

    private suspend fun save(edit: EffectsEdit) {
        val trackId = edit.trackId
        // A track outside the library keeps nothing of its own: its EQ is the global one.
        if (trackId == null || !trackEq.saveTrackEq(trackId, edit.settings.eq)) {
            repository.save(edit.settings)
            return
        }
        val global = repository.current()
        if (global.quality != edit.settings.quality) repository.save(global.copy(quality = edit.settings.quality))
    }

    companion object {
        /** As for the practice state (AutoSavePracticeStateUseCase). */
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
