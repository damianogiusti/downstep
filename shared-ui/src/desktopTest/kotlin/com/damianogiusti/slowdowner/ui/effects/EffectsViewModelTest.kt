package com.damianogiusti.slowdowner.ui.effects

import com.damianogiusti.slowdowner.domain.effects.ApplyEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EffectsSettingsRepository
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsViewModelTest {

    private val engine = FakeEffectsEngine()
    private val repository = FakeEffectsSettingsRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when the engine can normalize volume then the switch is offered with the saved value`() = runTest {
        repository.settings.value = EffectsSettings(isVolumeNormalized = true)
        val viewModel = viewModel()
        runCurrent()

        val state = requireNotNull(viewModel.state.value)
        assertTrue(state.canNormalizeVolume)
        assertTrue(state.settings.isVolumeNormalized)
    }

    @Test
    fun `when the engine cannot normalize volume then the switch is hidden`() = runTest {
        engine.canNormalizeVolume = false
        val viewModel = viewModel()
        runCurrent()

        assertFalse(requireNotNull(viewModel.state.value).canNormalizeVolume)
    }

    @Test
    fun `when volume normalization is toggled then it reaches the engine and is saved`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onVolumeNormalizationToggled(true)
        runCurrent()

        assertTrue(requireNotNull(viewModel.state.value).settings.isVolumeNormalized)
        assertTrue(engine.appliedVolumeNormalized)
        assertEquals(EffectsSettings(isVolumeNormalized = true), repository.settings.value)
    }

    private fun viewModel(): EffectsViewModel {
        val apply = ApplyEffectsSettingsUseCase(engine)
        return EffectsViewModel(
            engine = engine,
            observeSettings = ObserveEffectsSettingsUseCase(repository),
            updateSettings = UpdateEffectsSettingsUseCase(apply, repository, SilentLogger()),
        )
    }

    private class FakeEffectsEngine : EffectsEngine {
        override val mixRight = 0.5f
        override val mixSliderPosition = 0
        override val quality = ProcessingQuality.HIGH
        override var canNormalizeVolume = true
        var appliedVolumeNormalized = false

        override fun setMixFromSlider(position: Int) = Unit
        override fun setEqEnabled(enabled: Boolean) = Unit
        override fun setPreampDb(gainDb: Float) = Unit
        override fun setBandGainDb(band: Int, gainDb: Float) = Unit
        override fun setQuality(quality: ProcessingQuality) = Unit
        override fun setVolumeNormalized(enabled: Boolean) {
            appliedVolumeNormalized = enabled
        }
    }

    private class FakeEffectsSettingsRepository : EffectsSettingsRepository {
        val settings = MutableStateFlow(EffectsSettings())
        override fun observe() = settings
        override suspend fun current() = settings.value
        override suspend fun save(settings: EffectsSettings) {
            this.settings.value = settings
        }
    }

    private class SilentLogger : Logger {
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) = Unit
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
    }
}
