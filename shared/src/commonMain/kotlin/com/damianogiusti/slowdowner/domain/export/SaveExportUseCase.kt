package com.damianogiusti.slowdowner.domain.export

class SaveExportUseCase(private val repository: ExportRepository) {
    suspend operator fun invoke(file: ExportedFile, contentUri: String): Boolean =
        repository.saveTo(file, contentUri)

    /** A batch of files: all of them into [folder]. */
    suspend operator fun invoke(files: List<ExportedFile>, folder: String): Boolean =
        repository.saveAllTo(files, folder)
}
