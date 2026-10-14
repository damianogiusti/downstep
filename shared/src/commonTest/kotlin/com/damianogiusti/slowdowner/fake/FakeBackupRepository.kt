package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.backup.BackupProgress
import com.damianogiusti.slowdowner.domain.backup.BackupRepository
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.domain.backup.RestoreResult

/** Records what the use-cases ask for. */
class FakeBackupRepository : BackupRepository {
    var lastScope: BackupScope? = null
    var lastFileName: String? = null
    var lastRestoredUri: String? = null
    var lastConflictChoice: PlaylistConflictChoice? = null
    var playlistConflicts: List<String> = emptyList()
    var restoreResult: RestoreResult = RestoreResult.Success(0, 0, 0)

    override suspend fun export(
        scope: BackupScope,
        fileName: String,
        onProgress: (BackupProgress) -> Unit,
    ): BackupExportResult {
        lastScope = scope
        lastFileName = fileName
        return BackupExportResult.Success(BackupFile("/cache/$fileName", fileName))
    }

    override suspend fun saveTo(file: BackupFile, contentUri: String) = true

    override suspend fun findPlaylistConflicts(contentUri: String) = playlistConflicts

    override suspend fun restore(
        contentUri: String,
        onConflict: PlaylistConflictChoice,
        onProgress: (BackupProgress) -> Unit,
    ): RestoreResult {
        lastRestoredUri = contentUri
        lastConflictChoice = onConflict
        return restoreResult
    }
}
