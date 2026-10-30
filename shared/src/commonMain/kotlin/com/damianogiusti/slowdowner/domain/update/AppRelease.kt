package com.damianogiusti.slowdowner.domain.update

/**
 * A public release of the app. [canInstall]: the app can download it and replace itself. When false
 * (no file for this computer, or an app folder it cannot write), [pageUrl] has the download.
 * [notes]: what changed, one plain line each, in English; empty if the release says nothing.
 */
data class AppRelease(
    val version: AppVersion,
    val pageUrl: String,
    val canInstall: Boolean,
    val notes: List<String> = emptyList(),
)

/** [release] is newer than [installed]. */
data class AvailableUpdate(val installed: AppVersion, val release: AppRelease)

sealed interface UpdateCheckResult {
    data object Done : UpdateCheckResult
    /** Offline, or GitHub did not answer: the next launch tries again. */
    data object NetworkError : UpdateCheckResult
    data object Failure : UpdateCheckResult
}

sealed interface UpdateInstallResult {
    /** The new app is downloaded and checked: [RestartIntoUpdateUseCase] puts it in place. */
    data object ReadyToRestart : UpdateInstallResult
    data object NetworkError : UpdateInstallResult
    /** The download is not the expected file, or the app cannot be replaced. */
    data object Failure : UpdateInstallResult
}
