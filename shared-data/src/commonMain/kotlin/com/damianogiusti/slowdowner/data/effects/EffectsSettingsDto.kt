package com.damianogiusti.slowdowner.data.effects

data class EffectsSettingsDto(
    val eqEnabled: Boolean,
    val preampDb: Float,
    val bandGainsDb: List<Float>,
    val qualityName: String,
)
