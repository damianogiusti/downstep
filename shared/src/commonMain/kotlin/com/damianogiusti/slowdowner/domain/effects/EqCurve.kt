package com.damianogiusti.slowdowner.domain.effects

/**
 * The EQ as the user shapes it: up to [MAX_BANDS] bells ([EqBand]) and a preamp, on or off. Saved
 * per track and per preset; the global one (preferences) is what a track without its own starts
 * with. Isolate is not part of it: it is a separate stage that is never saved.
 */
data class EqCurve(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val bands: List<EqBand> = DEFAULT_BANDS,
) {
    /** Clamped into range, at most [MAX_BANDS] bands, never none: what may reach the engine. */
    fun sanitised(): EqCurve = EqCurve(
        enabled = enabled,
        preampDb = if (preampDb.isNaN()) 0f else preampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB),
        bands = bands.take(MAX_BANDS).map(EqBand::clamped).ifEmpty { DEFAULT_BANDS },
    )

    /** [index] replaced by [band], clamped. */
    fun withBand(index: Int, band: EqBand): EqCurve =
        if (index !in bands.indices) this
        else copy(bands = bands.toMutableList().also { it[index] = band.clamped() })

    /** Every band back at 0 dB where it is, the preamp at 0 dB; on stays on. */
    fun flattened(): EqCurve = copy(preampDb = 0f, bands = bands.map { it.copy(gainDb = 0f) })

    /** Nothing to hear: off, or every gain at 0 dB. */
    val isFlat: Boolean get() = !enabled || (preampDb == 0f && bands.all { it.gainDb == 0f })

    companion object {
        /** The engine's SD_EQ_MAX_BANDS. */
        const val MAX_BANDS = 8
        const val MIN_PREAMP_DB = -12f
        const val MAX_PREAMP_DB = 12f

        /** Where the first EQ's seven bands were: still the starting layout. */
        val DEFAULT_FREQUENCIES_HZ = listOf(60f, 150f, 400f, 1_000f, 2_400f, 6_000f, 15_000f)
        val DEFAULT_BANDS = DEFAULT_FREQUENCIES_HZ.map { EqBand(it, 0f, EqBand.DEFAULT_Q) }
        val FLAT = EqCurve()

        /**
         * The Q given to the old end bands. They were shelves; the engine now only has bells, and a
         * broad bell at the same frequency is the closest: it keeps the shelf's gain at its corner and
         * most of the audible range beyond it (below 60 Hz, above 15 kHz the shelf held its gain where
         * the bell rolls back to 0 dB).
         */
        const val LEGACY_SHELF_Q = 0.7f

        /**
         * Migrates the first EQ (seven fixed gains at [DEFAULT_FREQUENCIES_HZ]). The five middle bands
         * were already Q 1 bells: they map exactly. A list of another size was never valid and gives
         * the flat bands, as the old reader did.
         */
        fun fromLegacyGains(enabled: Boolean, preampDb: Float, gainsDb: List<Float>): EqCurve {
            val bands = if (gainsDb.size != DEFAULT_FREQUENCIES_HZ.size) DEFAULT_BANDS
            else DEFAULT_FREQUENCIES_HZ.mapIndexed { index, frequency ->
                val isEnd = index == 0 || index == DEFAULT_FREQUENCIES_HZ.lastIndex
                EqBand(frequency, gainsDb[index], if (isEnd) LEGACY_SHELF_Q else EqBand.DEFAULT_Q)
            }
            return EqCurve(enabled, preampDb, bands).sanitised()
        }
    }
}
