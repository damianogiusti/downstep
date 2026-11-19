package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.RenderRange

/** Records what was rendered; every render succeeds unless [result] says otherwise. */
class FakeExportRepository : ExportRepository {
    var exportedName: String? = null
    var exportedRange: RenderRange? = null
    var originalRange: RenderRange? = null
    /** Null: a successful render named after the call. */
    var result: ExportResult? = null

    override suspend fun exportOpenTrack(fileName: String, range: RenderRange, onProgress: (Float) -> Unit): ExportResult {
        exportedName = fileName
        exportedRange = range
        onProgress(1f)
        return result ?: ExportResult.Success(ExportedFile("/exports/$fileName", fileName))
    }

    override suspend fun renderOriginal(range: RenderRange, onProgress: (Float) -> Unit): ExportResult {
        originalRange = range
        onProgress(1f)
        return result ?: ExportResult.Success(ExportedFile("/renders/loop.mp3", "loop.mp3"))
    }

    override suspend fun saveTo(file: ExportedFile, contentUri: String) = error("unused")
}
