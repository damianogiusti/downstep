package com.damianogiusti.slowdowner.ui.effects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class EffectsViewModel(
    private val engine: EffectsEngine,
    observeSettings: ObserveEffectsSettingsUseCase,
    private val updateSettings: UpdateEffectsSettingsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<EffectsUiState?>(null)
    val state: StateFlow<EffectsUiState?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = EffectsUiState(observeSettings().first(), 0, 0).withMix()
            // StateFlow conflates bursts of slider moves; each value is applied in order.
            _state.filterNotNull()
                .map { it.settings }
                .distinctUntilChanged()
                .drop(1)
                .collect { updateSettings(it) }
        }
    }

    fun onMixChanged(sliderPosition: Int) {
        engine.setMixFromSlider(sliderPosition)
        _state.update { it?.withMix() }
    }

    fun onMixReset() = onMixChanged(SLIDER_MAX / 2)

    fun onEqToggled(enabled: Boolean) = edit { it.copy(eqEnabled = enabled) }

    fun onPreampChanged(gainDb: Float) = edit { it.copy(preampDb = gainDb) }

    fun onBandChanged(band: Int, gainDb: Float) = edit { settings ->
        settings.copy(bandGainsDb = settings.bandGainsDb.toMutableList().also { it[band] = gainDb })
    }

    fun onEqReset() = edit { it.copy(preampDb = 0f, bandGainsDb = EffectsSettings().bandGainsDb) }

    fun onQualitySelected(quality: ProcessingQuality) = edit { it.copy(quality = quality) }

    private fun edit(transform: (EffectsSettings) -> EffectsSettings) {
        _state.update { it?.copy(settings = transform(it.settings)) }
    }

    private fun EffectsUiState.withMix() = copy(
        mixSlider = engine.mixSliderPosition,
        mixRightPercent = (engine.mixRight * 100).roundToInt(),
    )
}
