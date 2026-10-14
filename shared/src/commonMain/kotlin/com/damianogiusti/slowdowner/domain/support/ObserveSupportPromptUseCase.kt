package com.damianogiusti.slowdowner.domain.support

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** The prompt to show, or null. */
class ObserveSupportPromptUseCase(
    private val repository: SupportPromptRepository,
    private val clock: Clock = Clock.System,
) {
    operator fun invoke(): Flow<SupportPrompt?> =
        repository.observe().map { SupportPromptRules.promptFor(it, clock.now()) }
}
