package com.damianogiusti.slowdowner.domain.update

import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    /** The version running now. Null in a development build, which never updates. */
    fun installedVersion(): AppVersion?

    /** The newest public release, once [refresh] has found one. */
    fun observeLatestRelease(): Flow<AppRelease?>

    suspend fun refresh(): UpdateCheckResult

    /** Downloads [release], checks it and gets it ready: nothing changes yet. [onProgress] goes from 0 to 1. */
    suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult

    /**
     * After a successful [install]: starts what replaces the app as soon as this process ends. The app
     * must quit right after. False if it cannot start: the installed app stays as it is.
     */
    suspend fun startSwap(): Boolean
}
