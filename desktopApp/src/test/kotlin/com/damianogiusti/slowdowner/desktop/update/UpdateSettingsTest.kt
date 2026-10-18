package com.damianogiusti.slowdowner.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateSettingsTest {

    @Test
    fun `when a packaged release starts then it checks the default repository`() {
        val settings = UpdateSettings.current("1.1.1", properties(PACKAGED))

        assertEquals("1.1.1", settings.installedVersion)
        assertEquals("https://api.github.com/repos/damianogiusti/downstep/releases/latest", settings.releasesUrl)
        assertEquals("https://github.com/damianogiusti/downstep/releases/latest", settings.releasesPageUrl)
    }

    @Test
    fun `when the build names a repository then a release checks that one`() {
        val settings = UpdateSettings.current("2.0.0", properties(PACKAGED, REPO to "someone/downstep-fork"))

        assertEquals("https://api.github.com/repos/someone/downstep-fork/releases/latest", settings.releasesUrl)
        assertEquals("https://github.com/someone/downstep-fork/releases/latest", settings.releasesPageUrl)
    }

    @Test
    fun `when the repository is not owner and name then the default stays`() {
        val settings = UpdateSettings.current("2.0.0", properties(PACKAGED, REPO to "https://evil.example/x"))

        assertEquals("https://api.github.com/repos/damianogiusti/downstep/releases/latest", settings.releasesUrl)
    }

    @Test
    fun `when a packaged dev build starts then it does not check`() {
        val settings = UpdateSettings.current("1.1.1-dev+3f2a9c1", properties(PACKAGED, REPO to "someone/downstep-fork"))

        assertEquals(UpdateSettings(), settings)
        assertNull(settings.installedVersion)
    }

    @Test
    fun `when the version is not one then it does not check`() {
        assertNull(UpdateSettings.current("dev", properties(PACKAGED)).installedVersion)
    }

    @Test
    fun `when a release runs from Gradle then it does not check`() {
        assertEquals(UpdateSettings(), UpdateSettings.current("1.1.1", properties()))
    }

    @Test
    fun `when a test sets the URL then even a dev build checks there`() {
        val settings = UpdateSettings.current(
            "1.1.1-dev+3f2a9c1",
            properties(PACKAGED, URL to "http://127.0.0.1:8080/latest", REPO to "someone/downstep-fork", INSTALL to "true"),
        )

        assertEquals("1.1.1-dev+3f2a9c1", settings.installedVersion)
        assertEquals("http://127.0.0.1:8080/latest", settings.releasesUrl)
        assertEquals("https://github.com/someone/downstep-fork/releases/latest", settings.releasesPageUrl)
        assertEquals(true, settings.installWithoutAsking)
    }

    private fun properties(vararg values: Pair<String, String>): (String) -> String? = mapOf(*values)::get

    private companion object {
        // A folder that is not an installation: the tests never touch a real app.
        val PACKAGED = "jpackage.app-path" to "/nowhere/Downstep"
        const val REPO = "slowdowner.releases.repo"
        const val URL = "slowdowner.updates.url"
        const val INSTALL = "slowdowner.updates.installWithoutAsking"
    }
}
