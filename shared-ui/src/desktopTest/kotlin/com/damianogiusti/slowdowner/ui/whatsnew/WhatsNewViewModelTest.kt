package com.damianogiusti.slowdowner.ui.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection
import com.damianogiusti.slowdowner.domain.whatsnew.RecordWhatsNewShownUseCase
import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.fake.FakeWhatsNewRepository
import com.damianogiusti.slowdowner.ui.fake.V1_3_0
import com.damianogiusti.slowdowner.ui.fake.getWhatsNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WhatsNewViewModelTest {

    private val repository = FakeWhatsNewRepository()
    private val logger = RecordingLogger()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when nothing is due then state is Hidden, the version is recorded and nothing is logged`() = runTest {
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(WhatsNewUiState.Hidden, viewModel.state.value)
        // Nothing newer in the changelog: the use case records the running version at once.
        assertEquals(listOf(V1_3_0), repository.recorded)
        assertEquals(emptyList(), logger.infos)
    }

    @Test
    fun `when versions were skipped then the sections show newest first`() = runTest {
        repository.lastSeen = version("1.1.0")
        repository.changelog = listOf(section("1.2.0", "Normalize"), section("1.3.0", "Loops", "Fixes"))
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(
            WhatsNewUiState.Visible(
                listOf(WhatsNewSection("1.3.0", listOf("Loops", "Fixes")), WhatsNewSection("1.2.0", listOf("Normalize"))),
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `when the popup opens then the version is recorded and one line is logged`() = runTest {
        repository.changelog = listOf(section("1.3.0", "Loops"))
        val viewModel = viewModel()
        subscribe(viewModel)

        assertEquals(listOf(V1_3_0), repository.recorded)
        assertEquals(listOf("What's new shown" to mapOf("sections" to "1")), logger.infos)
    }

    @Test
    fun `when OK is clicked then the popup closes`() = runTest {
        repository.changelog = listOf(section("1.3.0", "Loops"))
        val viewModel = viewModel()
        subscribe(viewModel)

        viewModel.onCloseClicked()
        runCurrent()

        assertEquals(WhatsNewUiState.Hidden, viewModel.state.value)
    }

    @Test
    fun `when the window subscribes again then the popup is not logged twice`() = runTest {
        repository.changelog = listOf(section("1.3.0", "Loops"))
        val viewModel = viewModel()
        subscribe(viewModel).cancel()
        runCurrent()
        subscribe(viewModel)

        assertEquals(1, logger.infos.size)
    }

    private fun viewModel() = WhatsNewViewModel(
        getWhatsNew(repository),
        RecordWhatsNewShownUseCase(repository, V1_3_0, logger),
    )

    private fun TestScope.subscribe(viewModel: WhatsNewViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun version(text: String) = requireNotNull(AppVersion.parse(text))

    private fun section(version: String, vararg lines: String) = ChangelogSection(version(version), lines.toList())

    private class RecordingLogger : Logger {
        val infos = mutableListOf<Pair<String, Map<String, String>>>()
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) {
            infos += message to properties
        }
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
    }
}
