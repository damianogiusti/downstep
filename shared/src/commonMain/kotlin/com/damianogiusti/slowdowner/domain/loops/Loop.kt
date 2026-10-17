package com.damianogiusti.slowdowner.domain.loops

/**
 * A named section of a track the user practises. [start] and [end] are fractions 0..1 of the
 * whole file, like [com.damianogiusti.slowdowner.domain.playback.LoopRegion].
 */
data class Loop(
    val id: Long,
    val trackId: String,
    val name: String,
    val start: Float,
    val end: Float,
    /** Times the loop wrapped during playback; reset by the user. */
    val passes: Int = 0,
    val notes: String = "",
    val progression: Progression = Progression(),
    val createdAt: Long = 0,
) {
    val length: Float get() = end - start

    val bounds: LoopBounds get() = LoopBounds(start, end)

    companion object {
        const val MAX_NAME_LENGTH = 40
        /** Far above any real note; keeps a pasted novel out of the database. */
        const val MAX_NOTES_LENGTH = 10_000
        private const val DEFAULT_NAME_PREFIX = "Loop "
        private val DEFAULT_NAME = Regex("""Loop (\d+)""")

        /** The trimmed name, or null if it is empty or too long. */
        fun normalizeName(name: String): String? = name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }

        /**
         * "Loop N" for a new loop: one above both the loop count and the highest "Loop N" already
         * there, so a new name never repeats one in use.
         */
        fun nextDefaultName(existing: List<Loop>): String {
            val highest = existing.mapNotNull { DEFAULT_NAME.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
            return DEFAULT_NAME_PREFIX + (maxOf(highest, existing.size) + 1)
        }
    }
}

/** Two points as fractions 0..1 of the file, [start] before [end]. */
data class LoopBounds(val start: Float, val end: Float) {
    val length: Float get() = end - start
}

/**
 * The loops of one track plus its per-track loop settings. [activeLoopId] is the loop that wraps
 * playback, gets edited and counts passes; it is null when the track has no loops, and never
 * points to a loop that is not in [loops].
 */
data class TrackLoops(
    val trackId: String,
    /** Sorted by start, then by id. */
    val loops: List<Loop> = emptyList(),
    val activeLoopId: Long? = null,
    /** Set by the user: there is no beat detection. Null when unknown. */
    val bpm: Float? = null,
    /** The user's toggle; snapping only happens while [bpm] is known too, see [isSnapping]. */
    val snapToBeat: Boolean = true,
) {
    val activeLoop: Loop? get() = activeLoopId?.let { id -> loops.firstOrNull { it.id == id } }

    val isSnapping: Boolean get() = snapToBeat && bpm != null

    /** The grid loop edits snap to, or null when they are free. */
    fun snapGrid(durationSeconds: Float): BeatGrid? = if (isSnapping) beatGrid(durationSeconds) else null

    /** The tempo grid, for beat counts and bar lines, whether or not edits snap to it. */
    fun beatGrid(durationSeconds: Float): BeatGrid? = bpm?.let { BeatGrid.of(it, durationSeconds) }
}
