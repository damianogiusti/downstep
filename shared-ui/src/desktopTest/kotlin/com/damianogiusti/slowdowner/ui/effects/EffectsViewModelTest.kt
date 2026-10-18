package com.damianogiusti.slowdowner.ui.effects

import androidx.lifecycle.ViewModelStore
import com.damianogiusti.slowdowner.data.playback.InMemoryNowPlayingRepository
import com.damianogiusti.slowdowner.domain.effects.ActiveEq
import com.damianogiusti.slowdowner.domain.effects.ApplyEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.EffectsEngine
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EffectsSettingsRepository
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.domain.effects.LoadTrackEqUseCase
import com.damianogiusti.slowdowner.domain.effects.ObserveEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import com.damianogiusti.slowdowner.domain.effects.SetBandIsolationUseCase
import com.damianogiusti.slowdowner.domain.effects.UpdateEffectsSettingsUseCase
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.ObserveNowPlayingUseCase
import com.damianogiusti.slowdowner.fake.FakeTrackEqRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsViewModelTest {

    private val engine = RecordingEffectsEngine()
    private val repository = CountingRepository()
    private val trackEq = FakeTrackEqRepository(library = setOf("a", "b"))
    private val nowPlaying = InMemoryNowPlayingRepository()
    private val activeEq = ActiveEq()
    private val apply = ApplyEffectsSettingsUseCase(engine)
    private val store = ViewModelStore()

    private val songA = LibraryTrack("a", "A", "/a.m4a", null, 0)
    private val songB = LibraryTrack("b", "B", "/b.m4a", null, 0)
    private val ownCurve = EqCurve(enabled = true, preampDb = -1f, bands = listOf(EqBand(100f, 6f, 0.8f), EqBand(5_000f, -3f, 2f)))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a node drag sounds at once and is saved with the track once, after it stops`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()

        listOf(1f, 2f, 3f).forEach { gain ->
            viewModel.onBandChanged(0, EqBand(70f, gain, 1.2f))
            runCurrent()
            assertEquals(EqBand(70f, gain, 1.2f), engine.bands[0])
            advanceTimeBy(100)
        }
        assertEquals(emptyList(), trackEq.saves)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(listOf("a"), trackEq.saves)
        assertEquals(EqBand(70f, 3f, 1.2f), trackEq.curves.getValue("a").bands[0])
        assertEquals(0, repository.saves)
    }

    @Test
    fun `a change right before the screen goes away is still saved`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()
        viewModel.onPreampChanged(-4f)
        runCurrent()

        store.clear()
        runCurrent()

        assertEquals(-4f, trackEq.curves.getValue("a").preampDb)
    }

    @Test
    fun `an edit inside the debounce is saved by the quit flush`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()
        viewModel.onBandChanged(2, EqBand(400f, 5f, 1f))
        runCurrent()
        advanceTimeBy(100)
        assertEquals(emptyList(), trackEq.saves)

        // The app quits: nothing advances the debounce or clears the store any more.
        viewModel.flushPendingSave()

        assertEquals(listOf("a"), trackEq.saves)
        assertEquals(5f, trackEq.curves.getValue("a").bands[2].gainDb)
    }

    @Test
    fun `a quit flush with nothing pending writes nothing`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()
        viewModel.onBandChanged(2, EqBand(400f, 5f, 1f))
        runCurrent()
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, trackEq.saves.size)

        viewModel.flushPendingSave()

        assertEquals(1, trackEq.saves.size)
    }

    @Test
    fun `a track opens with its own EQ, and one without plays the global EQ`() = runTest {
        repository.settings.value = EffectsSettings(eq = EqCurve(enabled = true, preampDb = 3f))
        trackEq.curves["a"] = ownCurve
        val viewModel = viewModel()
        runCurrent()

        nowPlaying.set(songA)
        runCurrent()
        assertEquals(ownCurve, viewModel.state.value!!.settings.eq)
        assertEquals("a", viewModel.state.value!!.trackId)
        assertEquals(ownCurve.bands, engine.bands)
        assertEquals(-1f, engine.appliedPreampDb)

        nowPlaying.set(songB)
        runCurrent()
        assertEquals(3f, viewModel.state.value!!.settings.eq.preampDb)
        assertEquals(3f, engine.appliedPreampDb)
        assertEquals(EqCurve.DEFAULT_BANDS, engine.bands)

        // Loading is not editing: nothing was written.
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS * 4)
        assertEquals(emptyList(), trackEq.saves)
        assertEquals(0, repository.saves)
    }

    @Test
    fun `a track switch inside the debounce saves the edit to the track it was made on`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()
        viewModel.onPreampChanged(-6f)
        runCurrent()
        advanceTimeBy(100)

        nowPlaying.set(songB)
        runCurrent()
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS * 4)

        assertEquals(listOf("a"), trackEq.saves)
        assertEquals(-6f, trackEq.curves.getValue("a").preampDb)
        assertNull(trackEq.curves["b"])
        assertEquals(0f, viewModel.state.value!!.settings.eq.preampDb)
    }

    @Test
    fun `with no track open the edits shape the global EQ`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.onEqToggled(true)
        runCurrent()
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        assertNull(viewModel.state.value!!.trackId)
        assertTrue(repository.settings.value.eq.enabled)
        assertEquals(emptyList(), trackEq.saves)
    }

    @Test
    fun `a preset's EQ becomes the track's own as an edit would`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()

        activeEq.request("a", ownCurve)
        runCurrent()
        assertEquals(ownCurve, viewModel.state.value!!.settings.eq)
        assertEquals(ownCurve.bands, engine.bands)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(ownCurve, trackEq.curves["a"])
        assertEquals(ownCurve, activeEq.curveOf("a"))
    }

    @Test
    fun `a preset's EQ asked for while its track is still loading is applied once it is in`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()

        trackEq.readDelayMillis = 1_000
        nowPlaying.set(songB)
        runCurrent()
        activeEq.request("b", ownCurve)
        runCurrent()
        assertEquals("a", viewModel.state.value!!.trackId)

        advanceTimeBy(1_001)
        assertEquals("b", viewModel.state.value!!.trackId)
        assertEquals(ownCurve, viewModel.state.value!!.settings.eq)
        assertEquals(ownCurve.bands, engine.bands)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 2_000)
        assertEquals(ownCurve, trackEq.curves["b"])
        assertNull(trackEq.curves["a"])
    }

    @Test
    fun `a double click puts a band back to 0 dB where it is, and reset brings the starting layout back`() = runTest {
        nowPlaying.set(songA)
        trackEq.curves["a"] = ownCurve
        val viewModel = viewModel()
        runCurrent()

        viewModel.onBandGainReset(0)
        assertEquals(EqBand(100f, 0f, 0.8f), viewModel.state.value!!.settings.eq.bands[0])
        assertEquals(EqBand(5_000f, -3f, 2f), viewModel.state.value!!.settings.eq.bands[1])

        viewModel.onEqReset()
        assertEquals(EqCurve(enabled = true), viewModel.state.value!!.settings.eq)
    }

    @Test
    fun `isolate changes the engine's isolation only, and clearing gives the full mix back`() = runTest {
        nowPlaying.set(songA)
        trackEq.curves["a"] = ownCurve
        val viewModel = viewModel()
        runCurrent()

        viewModel.onIsolationChanged(IsolationBands(low = true))
        runCurrent()
        assertEquals(IsolationBands.LOW, engine.isolationMask)
        assertEquals(ownCurve.bands, engine.bands)
        assertEquals(IsolationBands(low = true), viewModel.isolation.value)

        // An EQ edit meanwhile keeps Isolate on.
        viewModel.onPreampChanged(-2f)
        runCurrent()
        assertEquals(IsolationBands.LOW, engine.isolationMask)

        store.clear()
        runCurrent()

        assertEquals(0, engine.isolationMask)
        assertEquals(-2f, trackEq.curves.getValue("a").preampDb)
        assertEquals(0, repository.saves)
    }

    @Test
    fun `a quality change is saved to the preferences and does not tie the global EQ to the track`() = runTest {
        nowPlaying.set(songA)
        val viewModel = viewModel()
        runCurrent()

        viewModel.onQualitySelected(ProcessingQuality.LOW)
        runCurrent()
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        assertEquals(ProcessingQuality.LOW, engine.quality)
        assertEquals(ProcessingQuality.LOW, repository.settings.value.quality)
        // The track still plays the global EQ: it keeps following it.
        assertEquals(emptyList(), trackEq.saves)
        assertNull(trackEq.curves["a"])
    }

    private fun viewModel() = EffectsViewModel(
        engine,
        ObserveEffectsSettingsUseCase(repository),
        UpdateEffectsSettingsUseCase(apply, repository, trackEq),
        SetBandIsolationUseCase(engine),
        LoadTrackEqUseCase(trackEq, repository),
        ObserveNowPlayingUseCase(nowPlaying),
        activeEq,
    ).also { store.put("effects", it) }

    private class RecordingEffectsEngine : EffectsEngine {
        override val mixRight = 0.5f
        override val mixSliderPosition = 5_000
        override val quality get() = appliedQuality
        var appliedQuality = ProcessingQuality.HIGH
        var isEqOn = false
        var appliedPreampDb = 0f
        var bands: List<EqBand> = emptyList()
        var isolationMask = 0

        override fun setMixFromSlider(position: Int) = Unit
        override fun setEqEnabled(enabled: Boolean) { isEqOn = enabled }
        override fun setPreampDb(gainDb: Float) { appliedPreampDb = gainDb }
        override fun setEqBands(bands: List<EqBand>) { this.bands = bands }
        override fun setIsolation(mask: Int) { isolationMask = mask }
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
