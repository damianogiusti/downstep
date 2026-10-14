package com.damianogiusti.slowdowner.domain.backup

class SaveBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(file: BackupFile, contentUri: String): Boolean = repository.saveTo(file, contentUri)
}
