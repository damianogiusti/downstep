package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.data.whatsnew.PreferencesWhatsNewStore
import com.damianogiusti.slowdowner.data.whatsnew.WhatsNewStore
import java.io.File

/** How this run decides on the What's new popup. The defaults show none: tests. */
data class WhatsNewSettings(
    /** The running version as text; null or not a version ("dev"): no popup. */
    val appVersion: String? = null,
    /** Read before this run counts itself as a launch: it tells an update from a new installation. */
    val hasLaunchHistory: Boolean = false,
    val store: WhatsNewStore = PreferencesWhatsNewStore(),
    /** Where CHANGELOG.txt is (see syncChangelog in build.gradle.kts). Null outside the packaged app and Gradle runs. */
    val changelogDir: File? = System.getProperty("compose.application.resources.dir")?.let(::File),
) {
    companion object {
        /** Only the packaged app shows the popup and records a version: a Gradle run shows nothing and records nothing. */
        fun current(appVersion: String, hasLaunchHistory: Boolean, isPackaged: Boolean = isPackagedApp()) =
            WhatsNewSettings(appVersion = appVersion.takeIf { isPackaged }, hasLaunchHistory = hasLaunchHistory)
    }
}
