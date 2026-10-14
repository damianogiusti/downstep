package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.export.OfflineRenderer

/** MP3 export through the C engine: a second engine instance renders the file, playback goes on. */
class NativeOfflineRenderer(private val engine: NativeEngine) : OfflineRenderer {

    private var export: NativeEngine.NativeExport? = null

    override fun begin(outputPath: String): Boolean {
        export = engine.beginExport(outputPath)
        return export != null
    }

    override fun renderNext(): Float? = export?.next()

    override fun end() {
        export?.finish()
        export = null
    }
}
