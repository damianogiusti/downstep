package com.damianogiusti.slowdowner.ui.settings

import com.damianogiusti.slowdowner.domain.player.ObservePlayerPreferencesUseCase
import com.damianogiusti.slowdowner.domain.player.PlayerPreferences
import com.damianogiusti.slowdowner.domain.player.SetWaveformShownUseCase
import com.damianogiusti.slowdowner.ui.fake.FakePlayerPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakePlayerPreferencesRepository()
    private val viewModel by lazy {
        SettingsViewModel(ObservePlayerPreferencesUseCase(repository), SetWaveformShownUseCase(repository))
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `before the settings are read the state is loading`() {
        assertEquals(SettingsUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `the waveform starts off`() = runTest {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(SettingsUiState.Ready(isWaveformShown = false), viewModel.state.value)
    }

    @Test
    fun `when the switch is turned on then the waveform is saved on`() = runTest {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        viewModel.onWaveformShownChanged(isShown = true)
        runCurrent()

        assertTrue(repository.observe().first().isWaveformShown)
        assertEquals(SettingsUiState.Ready(isWaveformShown = true), viewModel.state.value)
    }

    @Test
    fun `a saved choice shows when the screen opens`() = runTest {
        repository.save(PlayerPreferences(isWaveformShown = true))
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(SettingsUiState.Ready(isWaveformShown = true), viewModel.state.value)
    }
}
