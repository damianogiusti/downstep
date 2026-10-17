package com.damianogiusti.slowdowner.ui.effects

import androidx.lifecycle.ViewModelStore
import com.damianogiusti.slowdowner.domain.effects.ApplyEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EffectsSettingsRepository
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.effects.SetBandIsolationUseCase
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
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

    private val engine = RecordingEffectsEngine()
    private val repository = CountingRepository()
    private val apply = ApplyEffectsSettingsUseCase(engine)
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
    fun `an edit inside the debounce is saved by the quit flush`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onBandChanged(2, 5f)
        runCurrent()
        advanceTimeBy(100)
        assertEquals(0, repository.saves)

        // The app quits: nothing advances the debounce or clears the store any more.
        viewModel.flushPendingSave()

        assertEquals(1, repository.saves)
        assertEquals(5f, repository.settings.value.bandGainsDb[2])
    }

    @Test
    fun `a quit flush with nothing pending writes nothing`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onBandChanged(2, 5f)
        runCurrent()
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, repository.saves)

        viewModel.flushPendingSave()

        assertEquals(1, repository.saves)
    }

    @Test
    fun `isolate changes the engine only, and clearing gives the user's EQ back`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIsolationChanged(IsolationBands(low = true))
        runCurrent()
        assertTrue(engine.isEqOn)
        assertEquals(EffectsSettings.MIN_GAIN_DB, engine.gains[6])
        assertEquals(IsolationBands(low = true), viewModel.isolation.value)

        store.clear()
        runCurrent()

        assertFalse(engine.isEqOn)
        assertEquals(0f, engine.gains[6])
        assertEquals(0, repository.saves)
    }

    private fun viewModel() = EffectsViewModel(
        engine,
        ObserveEffectsSettingsUseCase(repository),
        UpdateEffectsSettingsUseCase(apply, repository),
        SetBandIsolationUseCase(apply),
    ).also { store.put("effects", it) }

    private class RecordingEffectsEngine : EffectsEngine {
        override val mixRight = 0.5f
        override val mixSliderPosition = 5_000
        override val quality get() = appliedQuality
        var appliedQuality = ProcessingQuality.HIGH
        var isEqOn = false
        val gains = MutableList(7) { 0f }

        override fun setMixFromSlider(position: Int) = Unit
        override fun setEqEnabled(enabled: Boolean) { isEqOn = enabled }
        override fun setPreampDb(gainDb: Float) = Unit
        override fun setBandGainDb(band: Int, gainDb: Float) { gains[band] = gainDb }
        override fun setQuality(quality: ProcessingQuality) { appliedQuality = quality }
    }

    private class CountingRepository : EffectsSettingsRepository {
        val settings = MutableStateFlow(EffectsSettings())
        var saves = 0
        override fun observe() = settings
        override suspend fun current() = settings.value
        override suspend fun save(settings: EffectsSettings) {
            this.settings.value = settings
            saves++
        }
    }
}
