package com.damianogiusti.slowdowner.domain.export

class SaveExportUseCase(private val repository: ExportRepository) {
    suspend operator fun invoke(file: ExportedFile, contentUri: String): Boolean =
        repository.saveTo(file, contentUri)
}
