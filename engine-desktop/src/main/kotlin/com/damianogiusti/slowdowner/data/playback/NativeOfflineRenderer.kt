package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.data.export.OfflineRenderer
import com.damianogiusti.slowdowner.data.export.RenderRequest
import com.damianogiusti.slowdowner.data.export.RenderSound

/** MP3 export through the C engine: a second engine instance renders the file, playback goes on. */
class NativeOfflineRenderer(private val engine: NativeEngine) : OfflineRenderer {

    private var export: NativeEngine.NativeExport? = null

    override fun begin(outputPath: String, request: RenderRequest): Boolean {
        val sound = when (request.sound) {
            RenderSound.CURRENT -> NativeEngine.EXPORT_SOUND_CURRENT
            RenderSound.ORIGINAL -> NativeEngine.EXPORT_SOUND_ORIGINAL
        }
        val part = NativeEngine.ExportPart(request.range.start.toDouble(), request.range.end.toDouble(), sound)
        export = engine.beginExportPart(outputPath, part)
        return export != null
    }

    override fun renderNext(): Float? = export?.next()

    override fun end() {
        export?.finish()
        export = null
    }
}
