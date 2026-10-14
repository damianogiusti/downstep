package com.damianogiusti.slowdowner.domain.backup

/**
 * Merges a backup into the library: existing tracks, states and presets are never overwritten.
 * A playlist whose name is taken follows [onConflict].
 */
class RestoreBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(
        contentUri: String,
        onConflict: PlaylistConflictChoice,
        onProgress: (BackupProgress) -> Unit,
    ): RestoreResult = repository.restore(contentUri, onConflict, onProgress)
}
