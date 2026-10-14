package com.damianogiusti.slowdowner.domain.library


class ImportDocumentUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(contentUri: String): ImportResult = repository.importDocument(contentUri)
}
