package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.export.RenderRange
import kotlinx.coroutines.CompletableDeferred

/** Renders succeed; [gate], when set, holds every render until it completes. */
class FakeExportRepository : ExportRepository {
    var exportedRange: RenderRange? = null
    var originalRange: RenderRange? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun exportOpenTrack(fileName: String, range: RenderRange, onProgress: (Float) -> Unit): ExportResult {
        exportedRange = range
        gate?.await()
        return ExportResult.Success(ExportedFile("/exports/$fileName", fileName))
    }

    override suspend fun renderOriginal(range: RenderRange, onProgress: (Float) -> Unit): ExportResult {
        originalRange = range
        gate?.await()
        return ExportResult.Success(ExportedFile("/renders/loop.mp3", "loop.mp3"))
    }

    override suspend fun saveTo(file: ExportedFile, contentUri: String) = false
}
