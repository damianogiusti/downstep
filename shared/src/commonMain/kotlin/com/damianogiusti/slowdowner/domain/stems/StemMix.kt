package com.damianogiusti.slowdowner.domain.stems

/**
 * The mix of a separated track: the level of each stem the track has ([gains], 0..1, the keys are the stems that
 * exist), which are muted and which soloed, as the user left them. What plays is [effectiveGain], the one place
 * that knows how mute and solo combine with the level.
 */
data class StemMix(
    val gains: Map<StemKind, Float>,
    val mutedStems: Set<StemKind> = emptySet(),
    val soloedStems: Set<StemKind> = emptySet(),
) {
    /** Only the stems the track has count: a leftover flag of a stem that is not there silences nobody. */
    private val hasSolo: Boolean get() = soloedStems.any { it in gains }

    /** Mute wins over solo; a stem that is not soloed is silent while another one is; else the level. */
    fun effectiveGain(stem: StemKind): Float {
        val level = gains[stem] ?: return 0f
        if (stem in mutedStems) return 0f
        if (hasSolo && stem !in soloedStems) return 0f
        return level
    }

    /** True when the stem is silent only because another stem is soloed: it is not muted itself. */
    fun isSilencedBySolo(stem: StemKind): Boolean = stem in gains && stem !in mutedStems && hasSolo && stem !in soloedStems

    /** In the fixed order of [StemKind], whatever the order of [gains]. */
    val effectiveGains: Map<StemKind, Float> get() = gains.keys.sortedBy { it.ordinal }.associateWith(::effectiveGain)

    fun withMuted(stem: StemKind, isMuted: Boolean) = copy(mutedStems = if (isMuted) mutedStems + stem else mutedStems - stem)

    fun withSoloed(stem: StemKind, isSoloed: Boolean) = copy(soloedStems = if (isSoloed) soloedStems + stem else soloedStems - stem)

    fun withGain(stem: StemKind, gain: Float) = copy(gains = gains + (stem to gain))
}

/** Sets the effective gain of every stem of [mix] on the open stems. */
fun StemMixer.applyMix(mix: StemMix) {
    for ((stem, gain) in mix.effectiveGains) setGain(stem, gain)
}
