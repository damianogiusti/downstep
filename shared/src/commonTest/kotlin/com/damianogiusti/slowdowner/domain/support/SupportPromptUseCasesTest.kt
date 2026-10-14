package com.damianogiusti.slowdowner.domain.support

import com.damianogiusti.slowdowner.fake.FakeClock
import com.damianogiusti.slowdowner.fake.FakeSupportPromptRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class SupportPromptUseCasesTest {

    private val repository = FakeSupportPromptRepository()
    private val clock = FakeClock()
    private val recordLaunch = RecordAppLaunchUseCase(repository)
    private val observe = ObserveSupportPromptUseCase(repository, clock)
    private val dismiss = DismissSupportPromptUseCase(repository, clock)

    @Test
    fun `when fewer than three launches then no prompt`() = runTest {
        repeat(2) { recordLaunch() }

        assertNull(observe().first())
    }

    @Test
    fun `when third launch then prompt with close delay`() = runTest {
        repeat(3) { recordLaunch() }

        assertEquals(SupportPrompt(closeDelay = 5.seconds), observe().first())
    }

    @Test
    fun `when dismissed then no prompt before thirty days`() = runTest {
        repeat(3) { recordLaunch() }
        dismiss()
        clock.now += 29.days
        recordLaunch()

        assertNull(observe().first())
    }

    @Test
    fun `when thirty days passed then prompt without close delay`() = runTest {
        repeat(3) { recordLaunch() }
        dismiss()
        clock.now += 30.days
        recordLaunch()

        assertEquals(SupportPrompt(closeDelay = Duration.ZERO), observe().first())
    }

    @Test
    fun `when app quits before dismissing then close delay stays`() = runTest {
        repeat(4) { recordLaunch() }

        assertEquals(5.seconds, observe().first()?.closeDelay)
    }
}
