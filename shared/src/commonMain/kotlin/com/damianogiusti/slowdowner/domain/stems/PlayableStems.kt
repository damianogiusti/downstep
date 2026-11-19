package com.damianogiusti.slowdowner.domain.stems

/**
 * What it takes to play a separated track as stems: its [mode] and every stem file that is on disk, in a
 * fixed order (the order of [StemKind]). [stems] is never empty.
 */
data class PlayableStems(val mode: TrackMode, val stems: List<PlayableStem>) {
    /** The levels, mutes and solos of the stems, to play them with [StemMix.effectiveGain]. */
    val mix: StemMix
        get() = StemMix(
            gains = stems.associate { it.kind to it.gain },
            mutedStems = stems.filter { it.isMuted }.map { it.kind }.toSet(),
            soloedStems = stems.filter { it.isSoloed }.map { it.kind }.toSet(),
        )
}

/** [path] is absolute; [gain] is the saved level, 0..1, whatever [isMuted] and [isSoloed] say. */
data class PlayableStem(
    val kind: StemKind,
    val path: String,
    val gain: Float,
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
)
