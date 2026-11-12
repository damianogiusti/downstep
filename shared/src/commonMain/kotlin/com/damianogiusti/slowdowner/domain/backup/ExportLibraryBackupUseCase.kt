package com.damianogiusti.slowdowner.domain.backup

import kotlinx.datetime.LocalDateTime

/** Every track with its practice state and presets, plus every playlist. */
class ExportLibraryBackupUseCase(
    private val repository: BackupRepository,
    private val now: () -> LocalDateTime = ::localNow,
) {
    suspend operator fun invoke(onProgress: (BackupProgress) -> Unit): BackupExportResult =
        repository.export(BackupScope.Library, BackupFileName.of(BackupFileName.LIBRARY_LABEL, now()), onProgress)
}
