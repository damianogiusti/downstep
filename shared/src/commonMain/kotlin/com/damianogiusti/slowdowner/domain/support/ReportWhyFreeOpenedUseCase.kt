package com.damianogiusti.slowdowner.domain.support

import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.flow.first

/** The page the prompt is for. The prompt stays open, so this is not a close. */
class ReportWhyFreeOpenedUseCase(
    private val repository: SupportPromptRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(duringCountdown: Boolean) {
        val isFirst = repository.observe().first().lastShownAt == null
        logger.i(
            "Support prompt why free opened",
            mapOf(
                "occurrence" to SupportPromptRules.occurrence(isFirst),
                "duringCountdown" to duringCountdown.toString(),
            ),
        )
    }
}
