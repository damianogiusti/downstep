package com.damianogiusti.slowdowner.domain.backup

import com.damianogiusti.slowdowner.domain.playlists.Playlist
import kotlinx.datetime.LocalDate

/** The playlist and its order, plus its tracks with their practice state and presets. */
class ExportPlaylistBackupUseCase(
    private val repository: BackupRepository,
    private val today: () -> LocalDate = ::localToday,
) {
    suspend operator fun invoke(playlist: Playlist, onProgress: (BackupProgress) -> Unit): BackupExportResult =
        repository.export(BackupScope.Playlist(playlist.id), BackupFileName.of(playlist.name, today()), onProgress)
}
