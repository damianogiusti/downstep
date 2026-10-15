package com.damianogiusti.slowdowner.domain.update

import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    /** The version running now. Null in a development build, which never updates. */
    fun installedVersion(): AppVersion?

    /** The newest public release, once [refresh] has found one. */
    fun observeLatestRelease(): Flow<AppRelease?>

    suspend fun refresh(): UpdateCheckResult

    /** Downloads [release] and prepares the swap. [onProgress] goes from 0 to 1. */
    suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult
}
