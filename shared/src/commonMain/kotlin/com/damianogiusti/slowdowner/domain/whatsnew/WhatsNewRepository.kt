package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion

interface WhatsNewRepository {
    /** Every section of the changelog that comes with the app. Empty if it cannot be read. */
    suspend fun changelog(): List<ChangelogSection>

    /** The version whose changes the user was shown last. Null before 1.3.0 and on a new installation. */
    suspend fun lastSeenVersion(): AppVersion?

    suspend fun recordSeen(version: AppVersion)
}
