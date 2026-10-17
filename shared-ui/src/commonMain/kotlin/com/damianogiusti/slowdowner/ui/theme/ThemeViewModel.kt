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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The theme for the root of the window and the header's theme menu. The menu previews what the
 * pointer or the keyboard is on ([onPreview]) and goes back to the chosen theme when it leaves or
 * closes ([onPreviewEnd]); a click applies and saves ([onApply]). A preview is never saved, and it
 * only changes colours: nothing reloads.
 */
class ThemeViewModel(
    observeTheme: ObserveThemeUseCase,
    private val setTheme: SetThemeUseCase,
) : ViewModel() {

    // An id this version does not know shows (and checks) Downstep; the store keeps it as it is.
    private val saved = observeTheme().map(::known)

    /** Applied but not read back from the store yet: shown meanwhile, so a pick never flashes the old theme. */
    private val pending = MutableStateFlow<ThemeSelection?>(null)
    private val preview = MutableStateFlow<ThemeSelection?>(null)

    /** The chosen theme: what the menu checks. */
    val selection: StateFlow<ThemeSelection> = combine(saved, pending) { saved, pending -> pending ?: saved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSelection.DEFAULT)

    /** The theme on screen: the preview while there is one, else the chosen theme. */
    val effective: StateFlow<ThemeSelection> = combine(selection, preview) { selection, preview -> preview ?: selection }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSelection.DEFAULT)

    fun onPreview(selection: ThemeSelection) {
        preview.value = known(selection)
    }

    fun onPreviewEnd() {
        preview.value = null
    }

    fun onApply(selection: ThemeSelection) {
        val chosen = known(selection)
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
