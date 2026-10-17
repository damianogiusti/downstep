package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.effects.EqCurve

data class PracticePreset(
    val id: Long,
    val trackId: String,
    val name: String,
    val settings: PracticeSettings,
    /** The EQ saved with it; null for a preset saved before the EQ was per track: it leaves the EQ alone. */
    val eq: EqCurve? = null,
)
