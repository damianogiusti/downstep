package com.damianogiusti.slowdowner.domain.backup

/**
 * Backups of tracks, practice settings and playlists in one `.sldbackup` file. A restore
 * merges into the library: tracks, states and presets already there are kept, and only a
 * playlist can be overwritten, when the user chooses it.
 */
interface BackupRepository {
    /** Builds the file in app-private storage. Cancelling the caller deletes the partial file. */
    suspend fun export(scope: BackupScope, fileName: String, onProgress: (BackupProgress) -> Unit): BackupExportResult

    /** Copies [file] into a document the user picked. Returns false if the copy failed. */
    suspend fun saveTo(file: BackupFile, contentUri: String): Boolean

    /**
     * Names of the backup playlists that exist in the library with other tracks. Same name and
     * same tracks is not a conflict: the restore skips it. Empty when the file cannot be read:
     * the restore reports why.
     */
    suspend fun findPlaylistConflicts(contentUri: String): List<String>

    /** [contentUri] is a document picked or opened by the user. */
    suspend fun restore(
        contentUri: String,
        onConflict: PlaylistConflictChoice,
        onProgress: (BackupProgress) -> Unit,
    ): RestoreResult
}
