package com.damianogiusti.slowdowner.domain.backup

/** A backup file ready to share or save. [path] is app-private. */
data class BackupFile(val path: String, val name: String)

/** Tracks processed so far, out of [total]. [total] is 0 when there is no audio to copy. */
data class BackupProgress(val done: Int, val total: Int)

sealed interface BackupExportResult {
    data class Success(val file: BackupFile) : BackupExportResult
    /** The track or playlist is gone, or no audio file is left on disk. */
    data object NothingToExport : BackupExportResult
    data class Failure(val cause: Throwable) : BackupExportResult
}

sealed interface RestoreResult {
    /**
     * [tracksSkipped] were already in the library: kept as they are. [playlistsRestored] counts
     * new and overwritten playlists, not the ones that were already identical.
     */
    data class Success(val tracksAdded: Int, val tracksSkipped: Int, val playlistsRestored: Int) : RestoreResult
    /** Not a Downstep backup, damaged, or with unsafe entries. Nothing was changed. */
    data object InvalidFile : RestoreResult
    /** Written by a newer app version. Nothing was changed. */
    data class UnsupportedVersion(val version: Int) : RestoreResult
    /** I/O or database error. Files copied by this restore were removed. */
    data class Failure(val cause: Throwable) : RestoreResult
}
