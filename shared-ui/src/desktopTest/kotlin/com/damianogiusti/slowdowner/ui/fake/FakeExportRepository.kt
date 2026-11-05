package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.export.ExportRepository
import com.damianogiusti.slowdowner.domain.export.ExportResult
import com.damianogiusti.slowdowner.domain.export.ExportedFile

class FakeExportRepository : ExportRepository {
    override suspend fun exportOpenTrack(fileName: String, onProgress: (Float) -> Unit): ExportResult =
        ExportResult.Failure(IllegalStateException("not used"))
    override suspend fun saveTo(file: ExportedFile, contentUri: String) = false
}
