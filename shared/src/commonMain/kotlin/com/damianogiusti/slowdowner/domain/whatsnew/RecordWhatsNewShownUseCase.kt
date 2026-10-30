package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.logging.Logger

/** Called when the popup opens, not when it closes: a crash in between must not bring it back. */
class RecordWhatsNewShownUseCase(
    private val repository: WhatsNewRepository,
    private val running: AppVersion?,
    private val logger: Logger,
) {
    suspend operator fun invoke(sectionCount: Int) {
        running?.let { repository.recordSeen(it) }
        logger.i("What's new shown", mapOf("sections" to sectionCount.toString()))
    }
}
