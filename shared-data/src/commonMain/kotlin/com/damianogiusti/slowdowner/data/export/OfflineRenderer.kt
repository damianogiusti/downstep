package com.damianogiusti.slowdowner.data.export

/** Renders the open track to an MP3 file, chunk by chunk, with the engine's current settings. */
interface OfflineRenderer {
    /** Returns false if the engine cannot start rendering into [outputPath]. */
    fun begin(outputPath: String): Boolean

    /** Renders one chunk. Returns the progress (0..1), or null once the file is complete. */
    fun renderNext(): Float?

    /** Closes the output file. Always call it after a successful [begin]. */
    fun end()
}
