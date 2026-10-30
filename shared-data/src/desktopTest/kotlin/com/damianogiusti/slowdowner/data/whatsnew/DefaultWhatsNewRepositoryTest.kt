package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWhatsNewRepositoryTest {

    private val store = FakeStore()
    private val changelog = FakeChangelog()
    private val repository = DefaultWhatsNewRepository(store, changelog, UnconfinedTestDispatcher())

    @Test
    fun `when nothing is stored then there is no last seen version`() = runTest {
        assertNull(repository.lastSeenVersion())
    }

    @Test
    fun `when a version is recorded then it is read back`() = runTest {
        repository.recordSeen(requireNotNull(AppVersion.parse("1.3.0")))

        assertEquals("1.3.0", store.stored)
        assertEquals(AppVersion.parse("1.3.0"), repository.lastSeenVersion())
    }

    @Test
    fun `when the stored text is not a version then there is no last seen version`() = runTest {
        store.stored = "garbage"
        assertNull(repository.lastSeenVersion())
    }

    @Test
    fun `when the file is read then its sections come back as domain models`() = runTest {
        changelog.text = "## 1.3.0\n- One\n"

        val sections = repository.changelog()

        assertEquals(listOf("1.3.0"), sections.map { it.version.toString() })
        assertEquals(listOf("One"), sections.single().lines)
    }

    @Test
    fun `when the file is missing then the changelog is empty`() = runTest {
        changelog.text = null
        assertEquals(emptyList<Any>(), repository.changelog())
    }

    private class FakeStore : WhatsNewStore {
        var stored: String? = null
        override fun readLastSeenVersion() = stored
        override fun writeLastSeenVersion(version: String) {
            stored = version
        }
    }

    private class FakeChangelog : ChangelogDataSource {
        var text: String? = null
        override fun read() = text
    }
}
