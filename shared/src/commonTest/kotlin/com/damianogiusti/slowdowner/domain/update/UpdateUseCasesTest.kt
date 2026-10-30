package com.damianogiusti.slowdowner.domain.update

import com.damianogiusti.slowdowner.fake.FakeLogger
import com.damianogiusti.slowdowner.fake.FakeUpdateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateUseCasesTest {

    private val repository = FakeUpdateRepository(installed = version("1.0.3"))
    private val logger = FakeLogger()
    private val check = CheckForUpdateUseCase(repository, logger)
    private val observe = ObserveAvailableUpdateUseCase(repository)
    private val install = InstallUpdateUseCase(repository, logger)
    private val restart = RestartIntoUpdateUseCase(repository)

    @Test
    fun `when a newer release is published then it is available and logged`() = runTest {
        repository.published = release("1.0.4")

        check()

        assertEquals(AvailableUpdate(version("1.0.3"), release("1.0.4")), observe().first())
        assertEquals(
            FakeLogger.Line("Update available", mapOf("installed" to "1.0.3", "latest" to "1.0.4", "canInstall" to "true")),
            logger.infos.single(),
        )
    }

    @Test
    fun `when the restart is confirmed then the swap starts`() = runTest {
        assertEquals(true, restart())

        assertEquals(1, repository.swapsStarted)
    }

    @Test
    fun `when the swap cannot start then the restart says so`() = runTest {
        repository.canStartSwap = false

        assertEquals(false, restart())
    }

    @Test
    fun `when the latest release is the installed one then no update`() = runTest {
        repository.published = release("1.0.3")

        check()

        assertNull(observe().first())
        assertEquals(emptyList(), logger.infos)
    }

    @Test
    fun `when the latest release is older then no update`() = runTest {
        // A tester's build from CI can be ahead of the public release.
        repository.published = release("1.0.2")

        check()

        assertNull(observe().first())
    }

    @Test
    fun `when a development build then nothing is checked`() = runTest {
        repository.installed = null
        repository.published = release("1.0.4")

        assertEquals(UpdateCheckResult.Done, check())

        assertNull(observe().first())
    }

    @Test
    fun `when the check fails then no update and nothing is logged`() = runTest {
        repository.published = release("1.0.4")
        repository.checkResult = UpdateCheckResult.NetworkError

        assertEquals(UpdateCheckResult.NetworkError, check())

        assertNull(observe().first())
        assertEquals(emptyList(), logger.infos)
    }

    @Test
    fun `when installed then the outcome is logged`() = runTest {
        repository.installResult = UpdateInstallResult.Failure

        val result = install(release("1.0.4")) {}

        assertEquals(UpdateInstallResult.Failure, result)
        assertEquals(listOf(release("1.0.4")), repository.installRequests)
        assertEquals(
            FakeLogger.Line("Update install result", mapOf("installed" to "1.0.3", "latest" to "1.0.4", "outcome" to "failure")),
            logger.infos.single(),
        )
    }

    private fun release(version: String) = AppRelease(version(version), "https://example.com/$version", canInstall = true)

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))
}
