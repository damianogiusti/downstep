package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.data.update.GitHubReleaseDataSource

/** How this run looks for updates. The defaults look for none: tests and Gradle runs. */
data class UpdateSettings(
    /** Null: no check at all. */
    val installedVersion: String? = null,
    val releasesUrl: String = GitHubReleaseDataSource.LATEST_URL,
    /** Null: a newer version only links its download page. */
    val installation: AppInstallation? = null,
    /** For the end-to-end check in CI: it starts the update without a click. */
    val installWithoutAsking: Boolean = false,
) {
    companion object {
        /**
         * Only the packaged app checks: a Gradle run is not installed anywhere. The properties let a
         * test point an app at a local server (with JAVA_TOOL_OPTIONS, as the launcher has no flags).
         */
        fun current(appVersion: String): UpdateSettings {
            val releasesUrl = System.getProperty(URL_PROPERTY)?.takeIf { it.isNotBlank() }
            val isPackaged = System.getProperty("jpackage.app-path") != null
            if (!isPackaged && releasesUrl == null) return UpdateSettings()
            return UpdateSettings(
                installedVersion = appVersion,
                releasesUrl = releasesUrl ?: GitHubReleaseDataSource.LATEST_URL,
                installation = AppInstallation.current(),
                installWithoutAsking = System.getProperty(INSTALL_WITHOUT_ASKING_PROPERTY).toBoolean(),
            )
        }

        private const val URL_PROPERTY = "slowdowner.updates.url"
        private const val INSTALL_WITHOUT_ASKING_PROPERTY = "slowdowner.updates.installWithoutAsking"
    }
}
