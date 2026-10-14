package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        val settings = EffectsSettings(eqEnabled = true, quality = ProcessingQuality.LOW)

        repository.save(settings)

        assertEquals(settings, repository.observe().first())
        assertEquals("LOW", store.stored?.qualityName)
    }

    @Test
    fun `corrupted values fall back to safe defaults`() = runTest {
        store.stored = EffectsSettingsDto(
            eqEnabled = true,
            preampDb = 40f,
            bandGainsDb = listOf(1f, 2f),
            qualityName = "ULTRA",
        )

        val settings = repository.current()

        assertEquals(EffectsSettings.MAX_GAIN_DB, settings.preampDb)
        assertEquals(EffectsSettings().bandGainsDb, settings.bandGainsDb)
        assertEquals(ProcessingQuality.HIGH, settings.quality)
    }

    private class FakeStore : EffectsSettingsStore {
        var stored: EffectsSettingsDto? = null
        override fun read() = stored
        override fun write(settings: EffectsSettingsDto) { stored = settings }
    }
}
