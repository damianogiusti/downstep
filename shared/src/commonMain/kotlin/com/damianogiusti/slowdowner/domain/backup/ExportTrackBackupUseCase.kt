package com.damianogiusti.slowdowner.domain.backup

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.datetime.LocalDateTime

/** One track with its practice state and presets. */
class ExportTrackBackupUseCase(
    private val repository: BackupRepository,
    private val now: () -> LocalDateTime = ::localNow,
) {
    suspend operator fun invoke(track: LibraryTrack, onProgress: (BackupProgress) -> Unit): BackupExportResult =
        repository.export(BackupScope.Track(track.id), BackupFileName.of(track.title, now()), onProgress)
}
