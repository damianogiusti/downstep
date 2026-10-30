package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DefaultWhatsNewRepository(
    private val store: WhatsNewStore,
    private val changelog: ChangelogDataSource,
    private val dispatcher: CoroutineDispatcher, // injected (IO); swap in tests
) : WhatsNewRepository {

    override suspend fun changelog(): List<ChangelogSection> = withContext(dispatcher) {
        changelog.read()?.let(ChangelogMapper::toSections).orEmpty()
    }

    override suspend fun lastSeenVersion(): AppVersion? = withContext(dispatcher) {
        store.readLastSeenVersion()?.let(AppVersion::parse)
    }

    override suspend fun recordSeen(version: AppVersion) = withContext(dispatcher) {
        store.writeLastSeenVersion(version.toString())
    }
}
