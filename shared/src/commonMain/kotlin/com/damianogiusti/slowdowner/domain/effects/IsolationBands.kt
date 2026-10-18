package com.damianogiusti.slowdowner.domain.effects

/**
 * Isolate: solo the low, mid or high part of the mix, in any combination; none is the full mix.
 *
 * Not stem separation: the engine's isolation stage, after the EQ, pulls everything outside the
 * selection down 12 dB, and the user's own EQ stays as it is under it. That brings an instrument
 * forward rather than removing the others, which is what the engine can honestly do. It splits the
 * spectrum as the first EQ did: below ~250 Hz is low (bass and kick), up to ~4 kHz mid (the body of
 * voices, guitars and keys), above it high (the attack and air). Never saved, and never exported.
 */
data class IsolationBands(val low: Boolean = false, val mid: Boolean = false, val high: Boolean = false) {

    val isFullMix: Boolean get() = !low && !mid && !high

    /** For the engine: bit 0 low, bit 1 mid, bit 2 high; 0 = the full mix. */
    val mask: Int get() = (if (low) LOW else 0) or (if (mid) MID else 0) or (if (high) HIGH else 0)

    companion object {
        val FULL_MIX = IsolationBands()
        const val LOW = 1
        const val MID = 2
        const val HIGH = 4
    }
}
