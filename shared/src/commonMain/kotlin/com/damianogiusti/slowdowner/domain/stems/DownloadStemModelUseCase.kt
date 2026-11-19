package com.damianogiusti.slowdowner.domain.stems

class DownloadStemModelUseCase(private val repository: StemModelRepository) {
    suspend operator fun invoke(onProgress: (Float) -> Unit): ModelDownloadResult = repository.download(onProgress)
}
