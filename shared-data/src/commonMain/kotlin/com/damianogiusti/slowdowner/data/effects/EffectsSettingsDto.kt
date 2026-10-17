package com.damianogiusti.slowdowner.data.effects

/**
 * What the preferences hold. [eqCurveJson] is the EQ ([EqCurveCodec]); [legacyEq] the first EQ's
 * keys, read only while [eqCurveJson] is missing to migrate them, and removed by the next write.
 */
data class EffectsSettingsDto(
    val eqCurveJson: String?,
    val qualityName: String,
    val legacyEq: LegacyEqDto? = null,
)

/** The first EQ: seven fixed gains (60 Hz to 15 kHz), a preamp and a switch. */
data class LegacyEqDto(
    val enabled: Boolean,
    val preampDb: Float,
    val bandGainsDb: List<Float>,
)
