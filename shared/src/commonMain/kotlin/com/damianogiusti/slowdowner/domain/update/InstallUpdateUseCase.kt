package com.damianogiusti.slowdowner.domain.update

import com.damianogiusti.slowdowner.logging.Logger

/** The log line tells whether updates from the app work on people's computers. */
class InstallUpdateUseCase(
    private val repository: UpdateRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult {
        val result = repository.install(release, onProgress)
        val outcome = when (result) {
            UpdateInstallResult.ReadyToRestart -> "ready"
            UpdateInstallResult.NetworkError -> "network"
            UpdateInstallResult.Failure -> "failure"
        }
        logger.i(
            "Update install result",
            mapOf(
                "installed" to repository.installedVersion().toString(),
                "latest" to release.version.toString(),
                "outcome" to outcome,
            ),
        )
        return result
    }
}
