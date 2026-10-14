package com.damianogiusti.slowdowner.domain.update

import com.damianogiusti.slowdowner.logging.Logger

/** The base of the funnel: installs are counted against this. */
class ReportUpdatePromptShownUseCase(private val logger: Logger) {
    operator fun invoke(release: AppRelease) =
        logger.i("Update prompt shown", mapOf("latest" to release.version.toString(), "canInstall" to release.canInstall.toString()))
}
