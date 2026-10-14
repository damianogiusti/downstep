package com.damianogiusti.slowdowner.domain.backup

import kotlinx.datetime.LocalDate

/** Every track with its practice state and presets, plus every playlist. */
class ExportLibraryBackupUseCase(
    private val repository: BackupRepository,
    private val today: () -> LocalDate = ::localToday,
) {
    suspend operator fun invoke(onProgress: (BackupProgress) -> Unit): BackupExportResult =
        repository.export(BackupScope.Library, BackupFileName.of(BackupFileName.LIBRARY_LABEL, today()), onProgress)
}
