package com.damianogiusti.slowdowner.domain.effects

/**
 * Isolate: solo the low, mid or high part of the mix, in any combination; none is the full mix.
 *
 * Built on the 7-band EQ, not on stem separation: the bands outside the selection drop to the EQ's
 * floor ([EffectsSettings.MIN_GAIN_DB]), the selected ones keep the user's own curve. That brings an
 * instrument forward rather than removing the others, which is what the engine can honestly do.
 * Never saved: it is laid over the user's settings while it is on (see [SetBandIsolationUseCase]).
 */
data class IsolationBands(val low: Boolean = false, val mid: Boolean = false, val high: Boolean = false) {

    val isFullMix: Boolean get() = !low && !mid && !high

    /** What the engine plays: [settings] as they are for the full mix, else with the other bands cut. */
    fun applyTo(settings: EffectsSettings): EffectsSettings {
        if (isFullMix) return settings
        // With the EQ off the user hears it flat: keep that under the selection.
        val ownGains = if (settings.eqEnabled) settings.bandGainsDb else EffectsSettings().bandGainsDb
        return settings.copy(
            eqEnabled = true,
            preampDb = if (settings.eqEnabled) settings.preampDb else 0f,
            bandGainsDb = ownGains.mapIndexed { band, gainDb -> if (isSelected(band)) gainDb else EffectsSettings.MIN_GAIN_DB },
        )
    }

    private fun isSelected(band: Int) = when (band) {
        in LOW_BANDS -> low
        in MID_BANDS -> mid
        else -> high
    }

    companion object {
        val FULL_MIX = IsolationBands()

        // Over EffectsSettings.BAND_FREQUENCIES_HZ: 60 and 150 Hz are bass and kick, 400 Hz to 2.4 kHz
        // the body of voices, guitars and keys, 6 and 15 kHz the attack and air.
        private val LOW_BANDS = 0..1
        private val MID_BANDS = 2..4
    }
}
