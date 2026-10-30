package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion

internal object WhatsNewRules {

    /**
     * The sections to show, newest first. [hasLaunchHistory] tells a returning user from a new one: 1.2.0
     * and older never stored a last seen version, so a returning user without one counts as updated, and
     * we do not know from where. They get the section of the running version only.
     */
    fun sectionsToShow(
        running: AppVersion,
        lastSeen: AppVersion?,
        hasLaunchHistory: Boolean,
        changelog: List<ChangelogSection>,
    ): List<ChangelogSection> {
        val isShown: (ChangelogSection) -> Boolean = when {
            lastSeen != null -> { section -> section.version > lastSeen && section.version <= running }
            hasLaunchHistory -> { section -> section.version == running }
            else -> return emptyList()
        }
        return changelog.filter(isShown).filter { it.lines.isNotEmpty() }.sortedByDescending { it.version }
    }

    /** A downgrade keeps the newer version on record: going up again must not show the same notes twice. */
    fun shouldRecord(running: AppVersion, lastSeen: AppVersion?) = lastSeen == null || running > lastSeen
}
