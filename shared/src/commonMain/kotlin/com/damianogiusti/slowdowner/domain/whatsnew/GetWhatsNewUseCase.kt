package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The changes to show at this launch, newest first, or an empty list. [running] is null for a dev build.
 * [hasLaunchHistory] is read before this run counts itself as a launch.
 *
 * Decided once per process and then kept, because [IsWhatsNewDueUseCase] asks again while the popup
 * is open, and by then the version is already recorded. Registered as a single for that reason.
 * With nothing to show, the running version is recorded here: the popup records it when it opens.
 */
class GetWhatsNewUseCase(
    private val repository: WhatsNewRepository,
    private val running: AppVersion?,
    private val hasLaunchHistory: Boolean,
) {
    private val lock = Mutex()
    private var decided: List<ChangelogSection>? = null

    suspend operator fun invoke(): List<ChangelogSection> = lock.withLock {
        decided ?: decide().also { decided = it }
    }

    private suspend fun decide(): List<ChangelogSection> {
        val version = running ?: return emptyList()
        val lastSeen = repository.lastSeenVersion()
        val sections = WhatsNewRules.sectionsToShow(version, lastSeen, hasLaunchHistory, repository.changelog())
        if (sections.isEmpty() && WhatsNewRules.shouldRecord(version, lastSeen)) repository.recordSeen(version)
        return sections
    }
}
