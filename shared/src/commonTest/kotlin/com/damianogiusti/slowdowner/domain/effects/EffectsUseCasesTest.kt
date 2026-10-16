package com.damianogiusti.slowdowner.domain.effects

import com.damianogiusti.slowdowner.fake.FakeEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EffectsUseCasesTest {

    private val engine = FakeEngine()
    private val repository = InMemoryEffectsSettingsRepository()
    private val apply = ApplyEffectsSettingsUseCase(engine)
    private val update = UpdateEffectsSettingsUseCase(apply, repository)

    private val custom = EffectsSettings(
        eqEnabled = true,
        preampDb = -3f,
        bandGainsDb = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f),
        quality = ProcessingQuality.HIGH,
    )

    @Test
    fun `apply pushes every EQ value to the engine`() {
        apply(custom)

        assertTrue(engine.appliedEqEnabled)
        assertEquals(-3f, engine.appliedPreampDb)
        assertEquals(custom.bandGainsDb, engine.bandGainsDb)
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
    fun `update applies and saves`() = runTest {
        update(flowOf(custom))

        assertEquals(custom, repository.current())
        assertEquals(-3f, engine.appliedPreampDb)
    }

    @Test
    fun `update applies each change at once and saves once they settle`() = runTest {
        val edits = MutableSharedFlow<EffectsSettings>()
        val job = launch { update(edits) }
        runCurrent()

        listOf(-1f, -2f, -3f).forEach { preamp ->
            edits.emit(custom.copy(preampDb = preamp))
            runCurrent()
            assertEquals(preamp, engine.appliedPreampDb)
            advanceTimeBy(100)
        }
        assertEquals(0, repository.saves)

        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, repository.saves)
        assertEquals(-3f, repository.current().preampDb)
        job.cancel()
    }

    @Test
    fun `update saves the last change when cancelled before the pause`() = runTest {
        val edits = MutableSharedFlow<EffectsSettings>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(custom.copy(preampDb = -6f))
        runCurrent()

        job.cancel()
        runCurrent()

        assertEquals(1, repository.saves)
        assertEquals(-6f, repository.current().preampDb)
    }

    @Test
    fun `update does not save again what is already saved when cancelled`() = runTest {
        val edits = MutableSharedFlow<EffectsSettings>()
        val job = launch { update(edits) }
        runCurrent()
        edits.emit(custom)
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        job.cancel()
        runCurrent()

        assertEquals(1, repository.saves)
    }

    @Test
    fun `isolating low cuts the other bands to the EQ floor and keeps the user's own`() {
        SetBandIsolationUseCase(apply)(custom, IsolationBands(low = true))

        val floor = EffectsSettings.MIN_GAIN_DB
        assertEquals(listOf(1f, 2f, floor, floor, floor, floor, floor), engine.bandGainsDb)
        assertTrue(engine.appliedEqEnabled)
    }

    @Test
    fun `isolating with the EQ off keeps the selection flat and turns the EQ on`() {
        SetBandIsolationUseCase(apply)(EffectsSettings(preampDb = 4f), IsolationBands(mid = true, high = true))

        val floor = EffectsSettings.MIN_GAIN_DB
        assertEquals(listOf(floor, floor, 0f, 0f, 0f, 0f, 0f), engine.bandGainsDb)
        assertTrue(engine.appliedEqEnabled)
        assertEquals(0f, engine.appliedPreampDb)
    }

    @Test
    fun `full mix gives the user's own EQ back and nothing is saved`() = runTest {
        repository.save(custom.copy(eqEnabled = false))
        val saves = repository.saves
        val isolate = SetBandIsolationUseCase(apply)
        isolate(custom.copy(eqEnabled = false), IsolationBands(high = true))

        isolate(custom.copy(eqEnabled = false), IsolationBands.FULL_MIX)

        assertFalse(engine.appliedEqEnabled)
        assertEquals(custom.bandGainsDb, engine.bandGainsDb)
        assertEquals(saves, repository.saves)
        assertEquals(custom.copy(eqEnabled = false), repository.current())
    }

    @Test
    fun `an EQ edit while isolating keeps the overlay on the engine and saves the user's settings`() = runTest {
        val edits = MutableSharedFlow<EffectsSettings>()
        val job = launch { update(edits, isolation = { IsolationBands(low = true) }) }
        runCurrent()

        edits.emit(custom.copy(bandGainsDb = listOf(3f, 3f, 3f, 3f, 3f, 3f, 3f)))
        advanceTimeBy(UpdateEffectsSettingsUseCase.SAVE_DEBOUNCE_MS + 1)

        val floor = EffectsSettings.MIN_GAIN_DB
        assertEquals(listOf(3f, 3f, floor, floor, floor, floor, floor), engine.bandGainsDb)
        assertEquals(List(7) { 3f }, repository.current().bandGainsDb)
        job.cancel()
    }

    @Test
    fun `restore applies the saved settings`() = runTest {
        repository.save(custom.copy(quality = ProcessingQuality.VERY_HIGH))

        RestoreEffectsSettingsUseCase(apply, repository)()

        assertEquals(ProcessingQuality.VERY_HIGH, engine.quality)
        assertEquals(custom.bandGainsDb, engine.bandGainsDb)
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
