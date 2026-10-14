package com.damianogiusti.slowdowner.domain.update

import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.flow.first

/** Once per launch. The log line tells how many people run an old version, and which one. */
class CheckForUpdateUseCase(
    private val repository: UpdateRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(): UpdateCheckResult {
        val installed = repository.installedVersion() ?: return UpdateCheckResult.Done
        val result = repository.refresh()
        val latest = repository.observeLatestRelease().first()
        if (result == UpdateCheckResult.Done && latest != null && latest.version > installed) {
            logger.i(
                "Update available",
                mapOf("installed" to installed.toString(), "latest" to latest.version.toString(), "canInstall" to latest.canInstall.toString()),
            )
        }
        return result
    }
}
