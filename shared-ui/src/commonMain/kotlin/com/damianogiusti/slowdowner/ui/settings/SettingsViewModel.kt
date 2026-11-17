package com.damianogiusti.slowdowner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.player.ObservePlayerPreferencesUseCase
import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import com.damianogiusti.slowdowner.domain.player.SetWaveformShownUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(val isWaveformShown: Boolean) : SettingsUiState
}

class SettingsViewModel(
    observePlayerPreferences: ObservePlayerPreferencesUseCase,
    private val setWaveformShown: SetWaveformShownUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = observePlayerPreferences()
        .map<PlayerPreferences, SettingsUiState> { SettingsUiState.Ready(isWaveformShown = it.isWaveformShown) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun onWaveformShownChanged(isShown: Boolean) {
        viewModelScope.launch { setWaveformShown(isShown) }
    }
}
