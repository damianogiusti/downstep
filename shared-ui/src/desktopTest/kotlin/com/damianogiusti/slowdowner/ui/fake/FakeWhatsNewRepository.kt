package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection
import com.damianogiusti.slowdowner.domain.whatsnew.GetWhatsNewUseCase
import com.damianogiusti.slowdowner.domain.whatsnew.IsWhatsNewDueUseCase
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

val V1_3_0: AppVersion = requireNotNull(AppVersion.parse("1.3.0"))

/** A returning user on 1.3.0: with a section for it, What's new is due. */
fun getWhatsNew(repository: FakeWhatsNewRepository) = GetWhatsNewUseCase(repository, V1_3_0, hasLaunchHistory = true)

/** The gate of a launch where What's new is due, or not. */
fun whatsNewGate(isDue: Boolean) = IsWhatsNewDueUseCase(
    getWhatsNew(FakeWhatsNewRepository(changelog = if (isDue) listOf(ChangelogSection(V1_3_0, listOf("Loops"))) else emptyList())),
)
