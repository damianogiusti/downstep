package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.update.AppRelease
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.update.UpdateCheckResult
import com.damianogiusti.slowdowner.domain.update.UpdateInstallResult
import com.damianogiusti.slowdowner.domain.update.UpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUpdateRepository(var installed: AppVersion?) : UpdateRepository {

    val latest = MutableStateFlow<AppRelease?>(null)
    /** What the next [refresh] finds. */
    var published: AppRelease? = null
    var checkResult: UpdateCheckResult = UpdateCheckResult.Done
    var installResult: UpdateInstallResult = UpdateInstallResult.ReadyToRestart
    val installRequests = mutableListOf<AppRelease>()

    override fun installedVersion() = installed

    override fun observeLatestRelease(): Flow<AppRelease?> = latest

    override suspend fun refresh(): UpdateCheckResult {
        if (checkResult == UpdateCheckResult.Done) latest.value = published
        return checkResult
    }

    override suspend fun install(release: AppRelease, onProgress: (Float) -> Unit): UpdateInstallResult {
        installRequests += release
        onProgress(1f)
        return installResult
    }
}
