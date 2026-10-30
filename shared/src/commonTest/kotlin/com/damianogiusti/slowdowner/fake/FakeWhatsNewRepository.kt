package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewRepository

class FakeWhatsNewRepository(
    var changelog: List<ChangelogSection> = emptyList(),
    var lastSeen: AppVersion? = null,
) : WhatsNewRepository {

    val recorded = mutableListOf<AppVersion>()

    override suspend fun changelog() = changelog

    override suspend fun lastSeenVersion() = lastSeen

    override suspend fun recordSeen(version: AppVersion) {
        recorded += version
        lastSeen = version
    }
}
