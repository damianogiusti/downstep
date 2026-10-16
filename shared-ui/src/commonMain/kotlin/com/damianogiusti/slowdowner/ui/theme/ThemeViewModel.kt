package com.damianogiusti.slowdowner.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.theme.ObserveThemeModeUseCase
import com.damianogiusti.slowdowner.domain.theme.SetThemeModeUseCase
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The saved theme, for the root of the window: the only state the root observes. The header's
 * sun/moon button flips it.
 */
class ThemeViewModel(
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeMode: SetThemeModeUseCase,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> = observeThemeMode().stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    fun onToggle() {
        val next = if (mode.value == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark
        viewModelScope.launch { setThemeMode(next) }
    }
}
