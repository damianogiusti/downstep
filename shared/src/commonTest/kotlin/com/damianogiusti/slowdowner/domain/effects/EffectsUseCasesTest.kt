package com.damianogiusti.slowdowner.domain.effects

import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeTrackEqRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsUseCasesTest {

    private val engine = FakeEngine()
    private val repository = InMemoryEffectsSettingsRepository()
    private val trackEq = FakeTrackEqRepository(library = setOf("a", "b"))
    private val apply = ApplyEffectsSettingsUseCase(engine)
    private val update = UpdateEffectsSettingsUseCase(apply, repository, trackEq)

    private val curve = EqCurve(
        enabled = true,
        preampDb = -3f,
        bands = listOf(EqBand(80f, 4f, 0.7f), EqBand(1_000f, -2f, 2f), EqBand(9_000f, 5f, 1f)),
    )
    private val custom = EffectsSettings(eq = curve, quality = ProcessingQuality.HIGH)

    private fun global(settings: EffectsSettings) = EffectsEdit(trackId = null, settings = settings)
    private fun onTrack(trackId: String, settings: EffectsSettings) = EffectsEdit(trackId, settings)
    private fun withPreamp(preampDb: Float) = custom.copy(eq = curve.copy(preampDb = preampDb))

    @Test
    fun `apply pushes the whole curve to the engine`() {
        apply(custom)

        assertTrue(engine.appliedEqEnabled)
        assertEquals(-3f, engine.appliedPreampDb)
        assertEquals(curve.bands, engine.appliedEqBands)
    }

    @Test
    fun `apply rebuilds the player only when quality changes`() {
        apply(custom)
        assertEquals(0, engine.qualityChanges)

        apply(custom.copy(quality = ProcessingQuality.LOW))
        assertEquals(1, engine.qualityChanges)
        assertEquals(ProcessingQuality.LOW, engine.quality)
    }

    @Test
    fun `apply clamps what the engine could not take`() {
        val wild = EqCurve(true, 40f, List(10) { EqBand(50_000f, -30f, 0.01f) })

        apply(EffectsSettings(eq = wild))

        assertEquals(EqCurve.MAX_BANDS, engine.appliedEqBands.size)
        assertTrue(engine.appliedEqBands.all { it == EqBand(EqBand.MAX_FREQUENCY_HZ, EqBand.MIN_GAIN_DB, EqBand.MIN_Q) })
        assertEquals(EqCurve.MAX_PREAMP_DB, engine.appliedPreampDb)
    }

    @Test
    fun `update applies and saves the global EQ when no track is open`() = runTest {
        update(flowOf(global(custom)))

        assertEquals(custom, repository.current())
        assertEquals(-3f, engine.appliedPreampDb)
        assertTrue(trackEq.saves.isEmpty())
    }

    @Test
    fun `update applies each change at once and saves once they settle`() = runTest {
        val edits = MutableSharedFlow<EffectsEdit>()
        val job = launch { update(edits) }
        runCurrent()

        listOf(-1f, -2f, -3f).forEach { preamp ->
            edits.emit(global(withPreamp(preamp)))
            runCurrent()
            assertEquals(preamp, engine.appliedPreampDb)
            advanceTimeBy(100)
        }
        assertEquals(0, repository.saves)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, repository.saves)
        assertEquals(-3f, repository.current().eq.preampDb)
        job.cancel()
    }

    @Test
    fun `update saves the last change when cancelled before the pause`() = runTest {
        val edits = MutableSharedFlow<EffectsEdit>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(global(withPreamp(-6f)))
        runCurrent()

        job.cancel()
        runCurrent()

        assertEquals(1, repository.saves)
        assertEquals(-6f, repository.current().eq.preampDb)
    }

    @Test
    fun `update does not save again what is already saved when cancelled`() = runTest {
        val edits = MutableSharedFlow<EffectsEdit>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(global(custom))
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        job.cancel()
        runCurrent()

        assertEquals(1, repository.saves)
    }

    @Test
    fun `an edit on a track is saved with the track and leaves the global EQ alone`() = runTest {
        update(flowOf(onTrack("a", custom)))

        assertEquals(curve, trackEq.curves["a"])
        assertEquals(0, repository.saves)
    }

    @Test
    fun `a quality change on a track goes to the preferences, the curve to the track`() = runTest {
        update(flowOf(onTrack("a", custom.copy(quality = ProcessingQuality.LOW))))

        assertEquals(curve, trackEq.curves["a"])
        assertEquals(ProcessingQuality.LOW, repository.current().quality)
        assertEquals(EqCurve.FLAT, repository.current().eq)
    }

    @Test
    fun `an edit on a track outside the library is saved as the global EQ`() = runTest {
        update(flowOf(onTrack("spotify", custom)))

        assertTrue(trackEq.curves.isEmpty())
        assertEquals(custom, repository.current())
    }

    @Test
    fun `a track switch inside the pause saves the edit to the track it was made on`() = runTest {
        val edits = MutableSharedFlow<EffectsEdit>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(onTrack("a", withPreamp(-5f)))
        advanceTimeBy(100)

        // Track b opens: its EQ comes in as a load.
        val bCurve = EqCurve(enabled = true, preampDb = 2f)
        edits.emit(EffectsEdit("b", EffectsSettings(eq = bCurve), isLoad = true))
        runCurrent()

        assertEquals(-5f, trackEq.curves.getValue("a").preampDb)
        assertEquals(2f, engine.appliedPreampDb)
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS * 4)
        assertEquals(listOf("a"), trackEq.saves)
        assertNull(trackEq.curves["b"])
        job.cancel()
    }

    @Test
    fun `an edit for another track saves the pending one first`() = runTest {
        val edits = MutableSharedFlow<EffectsEdit>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(onTrack("a", withPreamp(-5f)))
        runCurrent()
        edits.emit(onTrack("b", withPreamp(-7f)))
        runCurrent()
        assertEquals(listOf("a"), trackEq.saves)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(listOf("a", "b"), trackEq.saves)
        assertEquals(-5f, trackEq.curves.getValue("a").preampDb)
        assertEquals(-7f, trackEq.curves.getValue("b").preampDb)
        job.cancel()
    }

    @Test
    fun `a load is played and never saved`() = runTest {
        update(flowOf(EffectsEdit("a", custom, isLoad = true)))

        assertEquals(curve.bands, engine.appliedEqBands)
        assertTrue(trackEq.saves.isEmpty())
        assertEquals(0, repository.saves)
    }

    @Test
    fun `isolate sets the engine's mask and leaves the EQ alone`() {
        apply(custom)
        val isolate = SetBandIsolationUseCase(engine)

        isolate(IsolationBands(low = true, high = true))
        assertEquals(IsolationBands.LOW or IsolationBands.HIGH, engine.isolationMask)
        assertEquals(curve.bands, engine.appliedEqBands)
        assertTrue(engine.appliedEqEnabled)

        isolate(IsolationBands.FULL_MIX)
        assertEquals(0, engine.isolationMask)
    }

    @Test
    fun `masks follow the selection`() {
        assertEquals(0, IsolationBands.FULL_MIX.mask)
        assertEquals(1, IsolationBands(low = true).mask)
        assertEquals(2, IsolationBands(mid = true).mask)
        assertEquals(4, IsolationBands(high = true).mask)
        assertEquals(7, IsolationBands(low = true, mid = true, high = true).mask)
    }

    @Test
    fun `an EQ edit while isolating keeps Isolate on and saves the user's own curve`() = runTest {
        SetBandIsolationUseCase(engine)(IsolationBands(mid = true))

        update(flowOf(onTrack("a", custom)))

        assertEquals(IsolationBands.MID, engine.isolationMask)
        assertEquals(curve.bands, engine.appliedEqBands)
        assertEquals(curve, trackEq.curves["a"])
    }

    @Test
    fun `restore applies the saved settings`() = runTest {
        repository.save(custom.copy(quality = ProcessingQuality.VERY_HIGH))

        RestoreEffectsSettingsUseCase(apply, repository)()

        assertEquals(ProcessingQuality.VERY_HIGH, engine.quality)
        assertEquals(curve.bands, engine.appliedEqBands)
    }

    @Test
    fun `a track plays its own EQ, else the global one`() = runTest {
        val load = LoadTrackEqUseCase(trackEq, repository)
        repository.save(EffectsSettings(eq = EqCurve(enabled = true, preampDb = -1f)))
        trackEq.saveTrackEq("a", curve)

        assertEquals(curve, load("a"))
        assertEquals(-1f, load("b").preampDb)
        assertEquals(-1f, load(null).preampDb)
    }

    @Test
    fun `a request waits for the editor and reads back the open track's curve`() = runTest {
        val active = ActiveEq()
        active.request("a", curve)

        assertEquals(ActiveEq.Current("a", curve), active.requests.first())
        active.publish("a", curve)
        assertEquals(curve, active.curveOf("a"))
        assertNull(active.curveOf("b"))
    }

    private class InMemoryEffectsSettingsRepository : EffectsSettingsRepository {
        private val settings = MutableStateFlow(EffectsSettings())
        var saves = 0
        override fun observe(): Flow<EffectsSettings> = settings
        override suspend fun current() = settings.value
        override suspend fun save(settings: EffectsSettings) {
            this.settings.value = settings
            saves++
        }
    }
}
