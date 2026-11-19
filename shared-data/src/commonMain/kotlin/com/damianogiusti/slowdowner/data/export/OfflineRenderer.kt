package com.damianogiusti.slowdowner.data.export

import com.damianogiusti.slowdowner.domain.export.RenderRange

/** Renders the open track to an MP3 file, chunk by chunk. */
interface OfflineRenderer {
    /** Returns false if the engine cannot start rendering [request] into [outputPath]. */
    fun begin(outputPath: String, request: RenderRequest): Boolean

    /** Renders one chunk. Returns the progress (0..1 over the range), or null once the file is complete. */
    fun renderNext(): Float?

    /** Closes the output file. Always call it after a successful [begin]. */
    fun end()
}

/** Which part of the open track to render, and with which sound. */
data class RenderRequest(val range: RenderRange, val sound: RenderSound)

enum class RenderSound {
    /** The engine's current speed, pitch and effects. */
    CURRENT,
    /** The file as it is: speed 100%, no pitch shift, no effects. */
    ORIGINAL,
}
