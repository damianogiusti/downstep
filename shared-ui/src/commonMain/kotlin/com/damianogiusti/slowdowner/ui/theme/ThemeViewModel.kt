package com.damianogiusti.slowdowner.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.theme.ObserveThemeUseCase
import com.damianogiusti.slowdowner.domain.theme.SetThemeUseCase
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The theme for the root of the window and the header's theme menu. The menu previews what the
 * pointer or the keyboard is on ([onPreview]) and goes back to the chosen theme when it leaves or
 * closes ([onPreviewEnd]); a click applies and saves ([onApply]). A preview is never saved, and it
 * only changes colours: nothing reloads.
 *
 * An id this version does not know (a newer version's theme) shows Downstep but stays the [selection]
 * as saved, so applying only a mode keeps it for that newer version.
 */
class ThemeViewModel(
    observeTheme: ObserveThemeUseCase,
    private val setTheme: SetThemeUseCase,
) : ViewModel() {

    // As saved, unknown ids included: only what is shown falls back to Downstep.
    private val saved = observeTheme()

    /** Applied but not read back from the store yet: shown meanwhile, so a pick never flashes the old theme. */
    private val pending = MutableStateFlow<ThemeSelection?>(null)
    private val preview = MutableStateFlow<ThemeSelection?>(null)

    /** The chosen theme, with the saved id even when unknown: the menu checks its [themeById]. */
    val selection: StateFlow<ThemeSelection> = combine(saved, pending) { saved, pending -> pending ?: saved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSelection.DEFAULT)

    /** The theme on screen: the preview while there is one, else the chosen theme; always a known id. */
    val effective: StateFlow<ThemeSelection> = combine(selection, preview) { selection, preview -> known(preview ?: selection) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSelection.DEFAULT)

    fun onPreview(selection: ThemeSelection) {
        preview.value = selection
    }

    fun onPreviewEnd() {
        preview.value = null
    }

    fun onApply(selection: ThemeSelection) {
        // Saved as given: a mode applied over an unknown id keeps that id.
        val chosen = selection
        pending.value = chosen
        preview.value = null
        viewModelScope.launch {
            try {
                setTheme(chosen)
                // Until the store has it, or another pick replaced it.
                combine(saved, pending) { saved, pending -> saved == chosen || pending != chosen }.first { it }
            } finally {
                pending.compareAndSet(chosen, null)
            }
        }
    }

    private fun known(selection: ThemeSelection) = selection.copy(themeId = themeById(selection.themeId).id)
}
