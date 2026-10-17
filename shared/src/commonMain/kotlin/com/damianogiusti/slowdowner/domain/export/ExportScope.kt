package com.damianogiusti.slowdowner.domain.export

/** What an export writes: the whole track, one file per loop, or both. */
enum class ExportScope {
    FULL_TRACK,
    LOOPS,
    BOTH;

    val includesFullTrack: Boolean get() = this != LOOPS
    val includesLoops: Boolean get() = this != FULL_TRACK

    /** The files a track with [loopCount] loops gets: a loop scope with no loops writes none of them. */
    fun fileCount(loopCount: Int): Int = (if (includesFullTrack) 1 else 0) + (if (includesLoops) loopCount else 0)
}

/** One file of an export batch: [start]..[end] of the track as fractions 0..1 (the whole track is 0..1). */
data class ExportItem(val fileName: String, val start: Float = 0f, val end: Float = 1f) {
    val length: Float get() = end - start
}
