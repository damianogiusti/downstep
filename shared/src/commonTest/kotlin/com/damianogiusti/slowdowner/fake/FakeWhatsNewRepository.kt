package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewLaunch
import com.damianogiusti.slowdowner.domain.whatsnew.WhatsNewRepository

class FakeWhatsNewRepository(
    var changelog: List<ChangelogSection> = emptyList(),
    var lastSeen: AppVersion? = null,
) : WhatsNewRepository {

    val recorded = mutableListOf<AppVersion>()

    private var snapshot: WhatsNewLaunch? = null

    /** Like the real repository: the first answer stays for the launch. */
    override suspend fun launch(): WhatsNewLaunch = snapshot ?: WhatsNewLaunch(lastSeen, changelog).also { snapshot = it }

    override suspend fun recordSeen(version: AppVersion) {
        recorded += version
        lastSeen = version
    }
}
