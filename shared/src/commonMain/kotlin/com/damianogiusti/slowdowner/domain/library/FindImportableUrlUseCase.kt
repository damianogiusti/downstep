package com.damianogiusti.slowdowner.domain.library


class FindImportableUrlUseCase(private val repository: LibraryRepository) {
    operator fun invoke(text: String?): String? = repository.findImportableUrl(text)
}
