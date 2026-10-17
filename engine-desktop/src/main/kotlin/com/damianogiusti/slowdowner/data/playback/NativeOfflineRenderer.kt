package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.export.OfflineRenderer

/**
 * MP3 export through the C engine: a second player renders over the open file's decoded audio,
 * playback goes on. One export at a time; each [begin] of a batch shares the same decoded audio.
 */
class NativeOfflineRenderer(private val engine: NativeEngine) : OfflineRenderer {

    private var export: NativeEngine.NativeExport? = null

    override fun begin(outputPath: String, start: Float, end: Float): Boolean {
        export = if (start <= 0f && end >= 1f) {
            engine.beginExport(outputPath)
        } else {
            engine.beginExport(outputPath, start.toDouble(), end.toDouble())
        }
        return export != null
    }

    override fun renderNext(): Float? = export?.next()

    override fun end() {
        export?.finish()
        export = null
    }
}
