package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings

data class EffectsUiState(
    val settings: EffectsSettings,
    val mixSlider: Int,
    /** 0..100, share of the right channel. */
    val mixRightPercent: Int,
)
