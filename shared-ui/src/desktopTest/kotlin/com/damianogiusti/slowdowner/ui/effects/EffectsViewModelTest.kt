package com.damianogiusti.slowdowner.ui.effects

import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.test.advanceTimeBy
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
    private val store = ViewModelStore()

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
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        assertTrue(requireNotNull(viewModel.state.value).settings.isVolumeNormalized)
        assertTrue(engine.appliedVolumeNormalized)
        assertEquals(EffectsSettings(isVolumeNormalized = true), repository.settings.value)
    }

    @Test
    fun `a slider drag sounds at once and is saved once, after it stops`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        listOf(1f, 2f, 3f).forEach { gain ->
            viewModel.onBandChanged(0, gain)
            runCurrent()
            assertEquals(gain, engine.gains[0])
            advanceTimeBy(100)
        }
        assertEquals(0, repository.saves)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, repository.saves)
        assertEquals(3f, repository.settings.value.bandGainsDb[0])
    }

    @Test
    fun `a change right before the screen goes away is still saved`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onPreampChanged(-4f)
        runCurrent()

        store.clear()
        runCurrent()

        assertEquals(-4f, repository.settings.value.preampDb)
    }

    @Test
    fun `quitting saves the change still waiting for its pause, once`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onPreampChanged(-5f)
        runCurrent()
        assertEquals(0, repository.saves)

        viewModel.flushPendingSave()
        viewModel.flushPendingSave()

        assertEquals(-5f, repository.settings.value.preampDb)
        assertEquals(1, repository.saves)
    }

    private fun viewModel(): EffectsViewModel {
        val apply = ApplyEffectsSettingsUseCase(engine)
        return EffectsViewModel(
            engine = engine,
            observeSettings = ObserveEffectsSettingsUseCase(repository),
            updateSettings = UpdateEffectsSettingsUseCase(apply, repository, SilentLogger()),
        ).also { store.put("effects", it) }
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
        val gains = MutableList(7) { 0f }
        override fun setBandGainDb(band: Int, gainDb: Float) { gains[band] = gainDb }
        override fun setQuality(quality: ProcessingQuality) = Unit
        override fun setVolumeNormalized(enabled: Boolean) {
            appliedVolumeNormalized = enabled
        }
    }

    private class FakeEffectsSettingsRepository : EffectsSettingsRepository {
        val settings = MutableStateFlow(EffectsSettings())
        var saves = 0
        override fun observe() = settings
        override suspend fun current() = settings.value
        override suspend fun save(settings: EffectsSettings) {
            this.settings.value = settings
            saves++
        }
    }

    private class SilentLogger : Logger {
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) = Unit
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
    }
}
