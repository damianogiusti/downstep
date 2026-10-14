package com.damianogiusti.slowdowner.domain.effects

/**
 * Effects kept across sessions, for every track. Speed, pitch and loop are saved per track
 * (see `domain/practice`); the L/R mix stays per session on purpose.
 */
data class EffectsSettings(
    val eqEnabled: Boolean = false,
    val preampDb: Float = 0f,
    val bandGainsDb: List<Float> = List(BAND_FREQUENCIES_HZ.size) { 0f },
    val quality: ProcessingQuality = ProcessingQuality.HIGH,
) {
    companion object {
        val BAND_FREQUENCIES_HZ = listOf(60, 150, 400, 1_000, 2_400, 6_000, 15_000)
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
    }
}
