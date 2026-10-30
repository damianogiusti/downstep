package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion

/**
 * The changes to show at this launch, newest first, or an empty list. [running] is null for a dev build.
 * [hasLaunchHistory] is read before this run counts itself as a launch.
 * With nothing to show, the running version is recorded here: the popup records it when it opens.
 */
class GetWhatsNewUseCase(
    private val repository: WhatsNewRepository,
    private val running: AppVersion?,
    private val hasLaunchHistory: Boolean,
) {
    suspend operator fun invoke(): List<ChangelogSection> {
        val version = running ?: return emptyList()
        val launch = repository.launch()
        val sections = WhatsNewRules.sectionsToShow(version, launch.lastSeen, hasLaunchHistory, launch.changelog)
        if (sections.isEmpty() && WhatsNewRules.shouldRecord(version, launch.lastSeen)) repository.recordSeen(version)
        return sections
    }
}
