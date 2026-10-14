package com.damianogiusti.slowdowner.domain.backup

/** Names of the backup playlists that would clash with the library: ask before restoring. */
class FindPlaylistConflictsUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(contentUri: String): List<String> = repository.findPlaylistConflicts(contentUri)
}
