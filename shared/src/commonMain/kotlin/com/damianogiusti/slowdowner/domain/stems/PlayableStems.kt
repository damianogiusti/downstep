package com.damianogiusti.slowdowner.domain.stems

/**
 * What it takes to play a separated track as stems: its [mode] and every stem file that is on disk, in a
 * fixed order (the order of [StemKind]). [stems] is never empty.
 */
data class PlayableStems(val mode: TrackMode, val stems: List<PlayableStem>)

/** [path] is absolute; [gain] is the saved level, 0..1. */
data class PlayableStem(val kind: StemKind, val path: String, val gain: Float)
