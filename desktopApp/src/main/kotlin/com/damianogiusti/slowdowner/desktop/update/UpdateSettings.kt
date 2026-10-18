package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.data.update.GitHubReleaseDataSource
import com.damianogiusti.slowdowner.domain.update.AppVersion

/** How this run looks for updates. The defaults look for none: tests and Gradle runs. */
data class UpdateSettings(
    /** Null: no check at all. */
    val installedVersion: String? = null,
    val releasesUrl: String = GitHubReleaseDataSource.latestUrl(GitHubReleaseDataSource.DEFAULT_REPO),
    /** The download page of a release that has none of its own. */
    val releasesPageUrl: String = GitHubReleaseDataSource.pageUrl(GitHubReleaseDataSource.DEFAULT_REPO),
    /** Null: a newer version only links its download page. */
    val installation: AppInstallation? = null,
    /** For the end-to-end check in CI: it starts the update without a click. */
    val installWithoutAsking: Boolean = false,
) {
    companion object {
        /**
         * Only a packaged release checks: a Gradle run is not installed anywhere, and a build from the
         * sources (a dev version, see AppVersion) must not replace itself with the latest release. The
         * build sets the repository the releases come from ([REPO_PROPERTY], like the version). The
         * URL property lets a test point any app at a local server (with JAVA_TOOL_OPTIONS, as the
         * launcher has no flags), a dev build too.
         */
        fun current(appVersion: String, property: (String) -> String? = System::getProperty): UpdateSettings {
            val releasesUrl = property(URL_PROPERTY)?.takeIf { it.isNotBlank() }
            val isPackaged = property("jpackage.app-path") != null
            val isRelease = AppVersion.parse(appVersion)?.isDevelopment == false
            if (releasesUrl == null && !(isPackaged && isRelease)) return UpdateSettings()
            val repo = property(REPO_PROPERTY)?.takeIf(GitHubReleaseDataSource::isValidRepo)
                ?: GitHubReleaseDataSource.DEFAULT_REPO
            return UpdateSettings(
                installedVersion = appVersion,
                releasesUrl = releasesUrl ?: GitHubReleaseDataSource.latestUrl(repo),
                releasesPageUrl = GitHubReleaseDataSource.pageUrl(repo),
                installation = if (isPackaged) AppInstallation.current() else null,
                installWithoutAsking = property(INSTALL_WITHOUT_ASKING_PROPERTY).toBoolean(),
            )
        }

        private const val URL_PROPERTY = "slowdowner.updates.url"
        private const val REPO_PROPERTY = "slowdowner.releases.repo"
        private const val INSTALL_WITHOUT_ASKING_PROPERTY = "slowdowner.updates.installWithoutAsking"
    }
}
