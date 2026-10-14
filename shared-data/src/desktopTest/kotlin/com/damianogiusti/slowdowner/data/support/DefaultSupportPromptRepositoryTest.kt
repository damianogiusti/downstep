package com.damianogiusti.slowdowner.data.support

import com.damianogiusti.slowdowner.domain.support.SupportPromptHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSupportPromptRepositoryTest {

    private val store = FakeStore()
    private val repository = DefaultSupportPromptRepository(store, UnconfinedTestDispatcher())

    @Test
    fun `when nothing is stored then history is empty`() = runTest {
        assertEquals(SupportPromptHistory(), repository.observe().first())
    }

    @Test
    fun `when launch is recorded then count grows from the stored value`() = runTest {
        store.stored = SupportPromptDto(launchCount = 2, lastShownAtMillis = null)

        repository.recordLaunch()

        assertEquals(3, repository.observe().first().launchCount)
        assertEquals(SupportPromptDto(launchCount = 3, lastShownAtMillis = null), store.stored)
    }

    @Test
    fun `when shown is recorded then the launch count is kept`() = runTest {
        val at = Instant.fromEpochMilliseconds(1_800_000_000_000)
        repository.recordLaunch()

        repository.recordShown(at)

        assertEquals(SupportPromptHistory(launchCount = 1, lastShownAt = at), repository.observe().first())
        assertEquals(1_800_000_000_000, store.stored?.lastShownAtMillis)
    }

    @Test
    fun `when stored count is negative then it reads as zero`() = runTest {
        store.stored = SupportPromptDto(launchCount = -4, lastShownAtMillis = null)

        assertEquals(0, repository.observe().first().launchCount)
    }

    private class FakeStore : SupportPromptStore {
        var stored: SupportPromptDto? = null
        override fun read() = stored
        override fun write(dto: SupportPromptDto) {
            stored = dto
        }
    }
}
