package com.damianogiusti.slowdowner.ui.theme

import com.damianogiusti.slowdowner.domain.theme.ObserveThemeUseCase
import com.damianogiusti.slowdowner.domain.theme.SetThemeUseCase
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.domain.theme.ThemeRepository
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val repository = FakeThemeRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the saved theme is chosen and on screen`() = runTest {
        repository.stored.value = LIGHT_FOREST
        val viewModel = viewModel()

        assertEquals(LIGHT_FOREST, viewModel.selection.value)
        assertEquals(LIGHT_FOREST, viewModel.effective.value)
    }

    @Test
    fun `a preview shows on screen but is neither chosen nor saved`() = runTest {
        val viewModel = viewModel()

        viewModel.onPreview(ThemeSelection(ThemeMode.Dark, "midnight"))

        assertEquals(ThemeSelection(ThemeMode.Dark, "midnight"), viewModel.effective.value)
        assertEquals(ThemeSelection.DEFAULT, viewModel.selection.value)
        assertEquals(emptyList(), repository.saves)
    }

    @Test
    fun `the end of a preview goes back to the chosen theme`() = runTest {
        repository.stored.value = LIGHT_FOREST
        val viewModel = viewModel()
        viewModel.onPreview(ThemeSelection(ThemeMode.Light, "dusk"))
        viewModel.onPreview(ThemeSelection(ThemeMode.Dark, "forest"))

        viewModel.onPreviewEnd()

        assertEquals(LIGHT_FOREST, viewModel.effective.value)
        assertEquals(emptyList(), repository.saves)
    }

    @Test
    fun `applying saves the theme, which stays on screen after the preview`() = runTest {
        val viewModel = viewModel()
        viewModel.onPreview(LIGHT_FOREST)

        viewModel.onApply(LIGHT_FOREST)
        viewModel.onPreviewEnd()
        advanceUntilIdle()

        assertEquals(listOf(LIGHT_FOREST), repository.saves)
        assertEquals(LIGHT_FOREST, viewModel.selection.value)
        assertEquals(LIGHT_FOREST, viewModel.effective.value)
    }

    @Test
    fun `while the store saves, the applied theme is shown rather than the old one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        viewModel.onApply(LIGHT_FOREST)
        // The menu closing ends any preview meanwhile.
        viewModel.onPreviewEnd()

        assertEquals(LIGHT_FOREST, viewModel.effective.value)
        assertEquals(LIGHT_FOREST, viewModel.selection.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(LIGHT_FOREST, viewModel.effective.value)
    }

    @Test
    fun `a pick replacing one still being saved wins`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        viewModel.onApply(LIGHT_FOREST)
        viewModel.onApply(ThemeSelection(ThemeMode.Dark, "dusk"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ThemeSelection(ThemeMode.Dark, "dusk"), viewModel.effective.value)
        assertEquals(ThemeSelection(ThemeMode.Dark, "dusk"), repository.stored.value)
    }

    @Test
    fun `a mode applied keeps the theme`() = runTest {
        repository.stored.value = ThemeSelection(ThemeMode.Dark, "rosewood")
        val viewModel = viewModel()

        viewModel.onApply(viewModel.selection.value.copy(mode = ThemeMode.Light))
        advanceUntilIdle()

        assertEquals(ThemeSelection(ThemeMode.Light, "rosewood"), repository.stored.value)
    }

    @Test
    fun `an unknown saved id shows and checks Downstep but stays in the store`() = runTest {
        repository.stored.value = ThemeSelection(ThemeMode.Light, "from-a-newer-version")
        val viewModel = viewModel()

        assertEquals(ThemeSelection(ThemeMode.Light, "downstep"), viewModel.selection.value)
        assertEquals(ThemeSelection(ThemeMode.Light, "downstep"), viewModel.effective.value)
        assertEquals(emptyList(), repository.saves)
    }

    @Test
    fun `an unknown id previewed shows Downstep`() = runTest {
        val viewModel = viewModel()

        viewModel.onPreview(ThemeSelection(ThemeMode.Light, "nope"))

        assertEquals(ThemeSelection(ThemeMode.Light, "downstep"), viewModel.effective.value)
    }

    private fun viewModel() = ThemeViewModel(ObserveThemeUseCase(repository), SetThemeUseCase(repository))

    private class FakeThemeRepository : ThemeRepository {
        val stored = MutableStateFlow(ThemeSelection.DEFAULT)
        val saves = mutableListOf<ThemeSelection>()

        /** Holds a save back (a slow disk) until completed. */
        var gate: CompletableDeferred<Unit>? = null

        override fun observe(): Flow<ThemeSelection> = stored

        override suspend fun save(selection: ThemeSelection) {
            gate?.await()
            saves += selection
            stored.value = selection
        }
    }

    private companion object {
        val LIGHT_FOREST = ThemeSelection(ThemeMode.Light, "forest")
    }
}
