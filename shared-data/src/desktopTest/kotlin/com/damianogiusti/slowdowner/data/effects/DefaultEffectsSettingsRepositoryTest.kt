package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEffectsSettingsRepositoryTest {

    private val store = FakeStore()
    private val repository = DefaultEffectsSettingsRepository(store, UnconfinedTestDispatcher())

    @Test
    fun `defaults when nothing is stored`() = runTest {
        assertEquals(EffectsSettings(), repository.current())
    }

    @Test
    fun `save persists and is observed`() = runTest {
        val curve = EqCurve(enabled = true, preampDb = -1.5f, bands = listOf(EqBand(90f, 3f, 0.8f), EqBand(4_000f, -2f, 3f)))
        val settings = EffectsSettings(eq = curve, quality = ProcessingQuality.LOW)

        repository.save(settings)

        assertEquals(settings, repository.observe().first())
        assertEquals("LOW", store.stored?.qualityName)
        assertEquals(curve, EqCurveCodec.decode(store.stored?.eqCurveJson))
        assertNull(store.stored?.legacyEq)
    }

    @Test
    fun `corrupted values fall back to safe defaults`() = runTest {
        store.stored = EffectsSettingsDto(eqCurveJson = "{not json", qualityName = "ULTRA")

        val settings = repository.current()

        assertEquals(EqCurve.FLAT, settings.eq)
        assertEquals(ProcessingQuality.HIGH, settings.quality)
    }

    @Test
    fun `the first EQ's settings are migrated when no curve is stored yet`() = runTest {
        store.stored = EffectsSettingsDto(
            eqCurveJson = null,
            qualityName = "MEDIUM",
            legacyEq = LegacyEqDto(enabled = true, preampDb = -3f, bandGainsDb = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f)),
        )

        val settings = repository.current()

        assertEquals(EqCurve.fromLegacyGains(true, -3f, listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f)), settings.eq)
        assertEquals(ProcessingQuality.MEDIUM, settings.quality)
    }

    @Test
    fun `old gains of the wrong size migrate to a flat EQ, as before`() = runTest {
        store.stored = EffectsSettingsDto(null, "HIGH", LegacyEqDto(enabled = true, preampDb = 40f, bandGainsDb = listOf(1f, 2f)))

        val settings = repository.current()

        assertEquals(EqCurve.DEFAULT_BANDS, settings.eq.bands)
        assertEquals(EqCurve.MAX_PREAMP_DB, settings.eq.preampDb)
    }

    @Test
    fun `a stored curve wins over leftover old keys`() = runTest {
        val curve = EqCurve(enabled = false, preampDb = 2f)
        store.stored = EffectsSettingsDto(EqCurveCodec.encode(curve), "HIGH", LegacyEqDto(true, -3f, List(7) { 5f }))

        assertEquals(curve, repository.current().eq)
    }

    private class FakeStore : EffectsSettingsStore {
        var stored: EffectsSettingsDto? = null
        override fun read() = stored
        override fun write(settings: EffectsSettingsDto) { stored = settings }
    }
}
