package com.damianogiusti.slowdowner.domain.support

import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Any action ends the prompt: the next one comes after the interval, without the close delay.
 * The log line tells which action works, and whether people act before the countdown ends.
 */
class DismissSupportPromptUseCase(
    private val repository: SupportPromptRepository,
    private val logger: Logger,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(action: SupportPromptAction, duringCountdown: Boolean) {
        val isFirst = repository.observe().first().lastShownAt == null
        repository.recordShown(clock.now())
        logger.i(
            "Support prompt closed",
            mapOf(
                "occurrence" to SupportPromptRules.occurrence(isFirst),
                "action" to action.logName,
                "duringCountdown" to duringCountdown.toString(),
            ),
        )
    }
}
