package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion

/**
 * What the popup is decided from: the last seen version and the changelog, as they were at the start
 * of this launch. [lastSeen] is null before 1.3.0 and on a new installation.
 */
data class WhatsNewLaunch(val lastSeen: AppVersion?, val changelog: List<ChangelogSection>)

interface WhatsNewRepository {
    /**
     * The same answer for the whole launch: recording the version must not change it, or the gate
     * would flip while the popup is open.
     */
    suspend fun launch(): WhatsNewLaunch

    suspend fun recordSeen(version: AppVersion)
}
