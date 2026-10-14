package com.damianogiusti.slowdowner.domain.support

import com.damianogiusti.slowdowner.fake.FakeClock
import com.damianogiusti.slowdowner.fake.FakeLogger
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
    private val logger = FakeLogger()
    private val recordLaunch = RecordAppLaunchUseCase(repository)
    private val observe = ObserveSupportPromptUseCase(repository, clock)
    private val reportShown = ReportSupportPromptShownUseCase(logger)
    private val dismiss = DismissSupportPromptUseCase(repository, logger, clock)

    @Test
    fun `when fewer than three launches then no prompt`() = runTest {
        repeat(2) { recordLaunch() }

        assertNull(observe().first())
    }

    @Test
    fun `when third launch then first prompt with close delay`() = runTest {
        repeat(3) { recordLaunch() }

        val prompt = observe().first()

        assertEquals(SupportPrompt(isFirst = true), prompt)
        assertEquals(5.seconds, prompt?.closeDelay)
    }

    @Test
    fun `when dismissed then no prompt before thirty days`() = runTest {
        repeat(3) { recordLaunch() }
        dismiss(SupportPromptAction.CLOSE, duringCountdown = false)
        clock.now += 29.days
        recordLaunch()

        assertNull(observe().first())
    }

    @Test
    fun `when thirty days passed then prompt without close delay`() = runTest {
        repeat(3) { recordLaunch() }
        dismiss(SupportPromptAction.CLOSE, duringCountdown = false)
        clock.now += 30.days
        recordLaunch()

        val prompt = observe().first()

        assertEquals(SupportPrompt(isFirst = false), prompt)
        assertEquals(Duration.ZERO, prompt?.closeDelay)
    }

    @Test
    fun `when app quits before dismissing then close delay stays`() = runTest {
        repeat(4) { recordLaunch() }

        assertEquals(5.seconds, observe().first()?.closeDelay)
    }

    @Test
    fun `when shown then occurrence is logged`() {
        reportShown(SupportPrompt(isFirst = true))
        reportShown(SupportPrompt(isFirst = false))

        assertEquals(
            listOf(
                FakeLogger.Line("Support prompt shown", mapOf("occurrence" to "first")),
                FakeLogger.Line("Support prompt shown", mapOf("occurrence" to "repeat")),
            ),
            logger.infos,
        )
    }

    @Test
    fun `when dismissed the first time then action and countdown are logged`() = runTest {
        repeat(3) { recordLaunch() }

        dismiss(SupportPromptAction.WHY_FREE, duringCountdown = true)

        assertEquals(
            FakeLogger.Line(
                "Support prompt closed",
                mapOf("occurrence" to "first", "action" to "why_free", "duringCountdown" to "true"),
            ),
            logger.infos.single(),
        )
    }

    @Test
    fun `when dismissed again then occurrence is repeat`() = runTest {
        repeat(3) { recordLaunch() }
        dismiss(SupportPromptAction.CLOSE, duringCountdown = false)
        clock.now += 30.days

        dismiss(SupportPromptAction.DONATE, duringCountdown = false)

        assertEquals(
            mapOf("occurrence" to "repeat", "action" to "donate", "duringCountdown" to "false"),
            logger.infos.last().properties,
        )
    }
}
