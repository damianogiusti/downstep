package com.damianogiusti.slowdowner.domain.support

/** Once per process start: the support prompt waits for the third launch. */
class RecordAppLaunchUseCase(private val repository: SupportPromptRepository) {
    suspend operator fun invoke() = repository.recordLaunch()
}
