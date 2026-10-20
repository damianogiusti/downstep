package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings

data class EffectsUiState(
    val settings: EffectsSettings,
    val mixSlider: Int,
    /** 0..100, share of the right channel. */
    val mixRightPercent: Int,
    /** The switch is shown only where the engine can measure loudness. */
    val canNormalizeVolume: Boolean = false,
)
