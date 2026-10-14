package com.damianogiusti.slowdowner.domain.support

import com.damianogiusti.slowdowner.logging.Logger

/** The base of the funnel: closed actions are counted against this. */
class ReportSupportPromptShownUseCase(private val logger: Logger) {
    operator fun invoke(prompt: SupportPrompt) =
        logger.i("Support prompt shown", mapOf("occurrence" to SupportPromptRules.occurrence(prompt.isFirst)))
}
