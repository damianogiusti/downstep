package com.damianogiusti.slowdowner.data.export

/** Renders the open track to an MP3 file, chunk by chunk, with the engine's current settings. */
interface OfflineRenderer {
    /**
     * Takes the engine's current sound settings (speed, pitch, mix, EQ) for a batch: every [begin]
     * until [endBatch] renders with them, so a change made while the batch runs reaches none of its
     * files, not only the later ones. Without it, each [begin] takes the settings as they are then.
     * Returns false if they cannot be taken.
     */
    fun beginBatch(): Boolean

    /** Lets the settings [beginBatch] took go. Always call it after [beginBatch], even if it failed. */
    fun endBatch()

    /**
     * Returns false if the engine cannot start rendering [start]..[end] (fractions 0..1 of the file;
     * the whole file by default) into [outputPath].
     */
    fun begin(outputPath: String, start: Float = 0f, end: Float = 1f): Boolean

    /** Renders one chunk. Returns the progress (0..1 of the range), or null once the file is complete. */
    fun renderNext(): Float?

    /**
     * Closes the output file. Always call it after a successful [begin]. Returns true if the file is
     * complete: false if the render was cut short (cancelled, or a write failed).
     */
    fun end(): Boolean
}
