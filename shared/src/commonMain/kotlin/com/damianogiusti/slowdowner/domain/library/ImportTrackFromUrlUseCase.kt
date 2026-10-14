package com.damianogiusti.slowdowner.domain.library


class ImportTrackFromUrlUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(url: String, onProgress: (ImportProgress) -> Unit): ImportResult =
        repository.importFromUrl(url, onProgress)
}
