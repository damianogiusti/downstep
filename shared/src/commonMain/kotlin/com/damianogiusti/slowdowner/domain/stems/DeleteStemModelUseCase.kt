package com.damianogiusti.slowdowner.domain.stems

class DeleteStemModelUseCase(private val repository: StemModelRepository) {
    suspend operator fun invoke() = repository.delete()
}
