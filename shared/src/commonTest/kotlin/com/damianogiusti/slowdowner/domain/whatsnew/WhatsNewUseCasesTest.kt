package com.damianogiusti.slowdowner.domain.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.fake.FakeLogger
import com.damianogiusti.slowdowner.fake.FakeWhatsNewRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsNewUseCasesTest {

    private val changelog = listOf(
        section("1.1.0", "Old"),
        section("1.2.0", "Normalize"),
        section("1.3.0", "Loops"),
        section("1.4.0", "Themes", "Fixes"),
    )
    private val repository = FakeWhatsNewRepository(changelog)

    private fun section(version: String, vararg lines: String) =
        ChangelogSection(requireNotNull(AppVersion.parse(version)), lines.toList())

    private fun version(text: String) = AppVersion.parse(text)

    private fun getWhatsNew(running: String?, hasLaunchHistory: Boolean) =
        GetWhatsNewUseCase(repository, running?.let(::version), hasLaunchHistory)

    private suspend fun shownVersions(running: String?, hasLaunchHistory: Boolean) =
        getWhatsNew(running, hasLaunchHistory)().map { it.version.toString() }

    @Test
    fun `when it is a new installation then nothing shows and the version is recorded`() = runTest {
        assertEquals(emptyList(), shownVersions("1.3.0", hasLaunchHistory = false))
        assertEquals(listOf(version("1.3.0")), repository.recorded)
    }

    @Test
    fun `when a returning user has no stored version then only the running version shows`() = runTest {
        assertEquals(listOf("1.3.0"), shownVersions("1.3.0", hasLaunchHistory = true))
    }

    @Test
    fun `when a returning user without a stored version has no section for the running version then nothing shows and it is recorded`() = runTest {
        assertEquals(emptyList(), shownVersions("1.3.1", hasLaunchHistory = true))
        assertEquals(listOf(version("1.3.1")), repository.recorded)
    }

    @Test
    fun `when several versions were skipped then all show, newest first`() = runTest {
        repository.lastSeen = version("1.1.0")
        assertEquals(listOf("1.4.0", "1.3.0", "1.2.0"), shownVersions("1.4.0", hasLaunchHistory = true))
    }

    @Test
    fun `when the changelog has sections newer than the running version then they are left out`() = runTest {
        repository.lastSeen = version("1.2.0")
        assertEquals(listOf("1.3.0"), shownVersions("1.3.0", hasLaunchHistory = true))
    }

    @Test
    fun `when the running version was already seen then nothing shows and nothing is recorded`() = runTest {
        repository.lastSeen = version("1.3.0")
        assertEquals(emptyList(), shownVersions("1.3.0", hasLaunchHistory = true))
        assertEquals(emptyList(), repository.recorded)
    }

    @Test
    fun `when the app was downgraded then nothing shows and the newer version stays on record`() = runTest {
        repository.lastSeen = version("1.4.0")
        assertEquals(emptyList(), shownVersions("1.3.0", hasLaunchHistory = true))
        assertEquals(emptyList(), repository.recorded)
        assertEquals(version("1.4.0"), repository.lastSeen)
    }

    @Test
    fun `when it is a dev build then nothing shows and nothing is recorded`() = runTest {
        assertEquals(emptyList(), shownVersions(running = null, hasLaunchHistory = true))
        assertEquals(emptyList(), repository.recorded)
    }

    @Test
    fun `when a section has no lines then it is left out`() = runTest {
        repository.changelog = listOf(section("1.3.0"))
        assertEquals(emptyList(), shownVersions("1.3.0", hasLaunchHistory = true))
    }

    @Test
    fun `when the version recorded is older but nothing is new in the changelog then the running version is recorded`() = runTest {
        repository.lastSeen = version("1.4.0")
        repository.changelog = emptyList()
        assertEquals(emptyList(), shownVersions("1.5.0", hasLaunchHistory = true))
        assertEquals(listOf(version("1.5.0")), repository.recorded)
    }

    @Test
    fun `when sections are due then the gate is closed`() = runTest {
        assertTrue(IsWhatsNewDueUseCase(getWhatsNew("1.3.0", hasLaunchHistory = true))())
    }

    @Test
    fun `when nothing is due then the gate is open`() = runTest {
        assertFalse(IsWhatsNewDueUseCase(getWhatsNew("1.3.0", hasLaunchHistory = false))())
    }

    @Test
    fun `when the popup is shown then the running version is recorded and one line is logged`() = runTest {
        val logger = FakeLogger()
        RecordWhatsNewShownUseCase(repository, version("1.4.0"), logger)(sectionCount = 3)

        assertEquals(listOf(version("1.4.0")), repository.recorded)
        assertEquals(listOf(FakeLogger.Line("What's new shown", mapOf("sections" to "3"))), logger.infos)
    }
}
