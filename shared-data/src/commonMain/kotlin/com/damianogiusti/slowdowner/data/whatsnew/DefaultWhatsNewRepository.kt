package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewLaunch
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bind as a single: it keeps the first [launch] for the whole process. */
class DefaultWhatsNewRepository(
    private val store: WhatsNewStore,
    private val changelog: ChangelogDataSource,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : WhatsNewRepository {

    private val lock = Mutex()
    private var launch: WhatsNewLaunch? = null

    override suspend fun launch(): WhatsNewLaunch = lock.withLock {
        launch ?: withContext(dispatcher) {
            WhatsNewLaunch(
                lastSeen = store.readLastSeenVersion()?.let(AppVersion::parse),
                changelog = changelog.read()?.let(ChangelogMapper::toSections).orEmpty(),
            )
        }.also { launch = it }
    }

    override suspend fun recordSeen(version: AppVersion) = withContext(dispatcher) {
        store.writeLastSeenVersion(version.toString())
    }
}
