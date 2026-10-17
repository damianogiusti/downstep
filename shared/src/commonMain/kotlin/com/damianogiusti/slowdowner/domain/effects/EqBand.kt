package com.damianogiusti.slowdowner.domain.effects

/**
 * One bell of the EQ: a peaking filter centred on [frequencyHz] that lifts or cuts by [gainDb], over
 * a width set by [q] (higher is narrower). The engine runs exactly this filter (an RBJ peaking
 * biquad); [EqResponse] draws it.
 */
data class EqBand(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float = DEFAULT_Q,
) {
    /** Inside the ranges the engine accepts; anything not a number falls back to a neutral value. */
    fun clamped(): EqBand = EqBand(
        frequencyHz = frequencyHz.orIfNaN(1_000f).coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ),
        gainDb = gainDb.orIfNaN(0f).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB),
        q = q.orIfNaN(DEFAULT_Q).coerceIn(MIN_Q, MAX_Q),
    )

    companion object {
        const val MIN_FREQUENCY_HZ = 20f
        const val MAX_FREQUENCY_HZ = 20_000f
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        const val MIN_Q = 0.3f
        const val MAX_Q = 10f
        const val DEFAULT_Q = 1f

        private fun Float.orIfNaN(fallback: Float) = if (isNaN()) fallback else this
    }
}
