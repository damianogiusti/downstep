package com.damianogiusti.slowdowner.domain.support

import kotlin.time.Clock

/** Any button counts: the next prompt comes after the interval, without the close delay. */
class DismissSupportPromptUseCase(
    private val repository: SupportPromptRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke() = repository.recordShown(clock.now())
}
