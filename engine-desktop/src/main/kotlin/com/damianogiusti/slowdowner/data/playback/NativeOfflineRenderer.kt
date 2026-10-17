package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.export.OfflineRenderer

/**
 * MP3 export through the C engine: a second player renders over the open file's decoded audio,
 * playback goes on. One export at a time; each [begin] of a batch shares the same decoded audio and the
 * sound settings [beginBatch] took.
 */
class NativeOfflineRenderer(private val engine: NativeEngine) : OfflineRenderer {

    private var export: NativeEngine.NativeExport? = null
    private var batch: NativeEngine.ExportSettings? = null

    override fun beginBatch(): Boolean {
        endBatch()
        batch = engine.captureExportSettings()
        return batch != null
    }

    override fun endBatch() {
        batch?.close()
        batch = null
    }

    override fun begin(outputPath: String, start: Float, end: Float): Boolean {
        val settings = batch
        export = when {
            settings != null -> engine.beginExport(outputPath, start.toDouble(), end.toDouble(), settings)
            start <= 0f && end >= 1f -> engine.beginExport(outputPath)
            else -> engine.beginExport(outputPath, start.toDouble(), end.toDouble())
        }
        return export != null
    }

    override fun renderNext(): Float? = export?.next()

    override fun end(): Boolean {
        val isComplete = export?.finish() ?: false
        export = null
        return isComplete
    }
}
