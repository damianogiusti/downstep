package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings

data class EffectsUiState(
    /** The open track's EQ (else the global one) and the quality. */
    val settings: EffectsSettings,
    val mixSlider: Int,
    /** 0..100, share of the right channel. */
    val mixRightPercent: Int,
    /** The track the EQ is saved with; null: no track is open, the EQ edited is the global one. */
    val trackId: String? = null,
)
