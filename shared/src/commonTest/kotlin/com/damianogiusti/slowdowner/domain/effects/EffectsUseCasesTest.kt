package com.damianogiusti.slowdowner.domain.effects

import com.damianogiusti.slowdowner.fake.FakeEngine
import com.damianogiusti.slowdowner.fake.FakeLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class EffectsUseCasesTest {

    private val engine = FakeEngine()
    private val repository = InMemoryEffectsSettingsRepository()
    private val apply = ApplyEffectsSettingsUseCase(engine)
    private val logger = FakeLogger()
    private val update = UpdateEffectsSettingsUseCase(apply, repository, logger)

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
    fun `apply pushes volume normalization to the engine`() {
        apply(custom.copy(isVolumeNormalized = true))
        assertTrue(engine.appliedVolumeNormalized)

        apply(custom)
        assertFalse(engine.appliedVolumeNormalized)
    }

    @Test
    fun `update applies and saves`() = runTest {
        update(custom)

        assertEquals(custom, repository.current())
        assertEquals(-3f, engine.appliedPreampDb)
    }

    @Test
    fun `restore applies the saved settings`() = runTest {
        repository.save(custom.copy(quality = ProcessingQuality.VERY_HIGH, isVolumeNormalized = true))

        RestoreEffectsSettingsUseCase(apply, repository)()

        assertEquals(ProcessingQuality.VERY_HIGH, engine.quality)
        assertEquals(custom.bandGainsDb, engine.bandGainsDb)
        assertTrue(engine.appliedVolumeNormalized)
    }

    @Test
    fun `when volume normalization is switched then each change is logged once`() = runTest {
        update(custom.copy(isVolumeNormalized = true))
        update(custom.copy(isVolumeNormalized = true, preampDb = 2f))
        update(custom.copy(isVolumeNormalized = false))

        assertEquals(
            listOf(
                FakeLogger.Line("Volume normalization changed", mapOf("enabled" to "true")),
                FakeLogger.Line("Volume normalization changed", mapOf("enabled" to "false")),
            ),
            logger.infos,
        )
    }

    @Test
    fun `when only the EQ changes then nothing is logged`() = runTest {
        update(custom)
        update(custom.copy(preampDb = 1f))

        assertEquals(emptyList(), logger.infos)
    }

    private class InMemoryEffectsSettingsRepository : EffectsSettingsRepository {
        private val settings = MutableStateFlow(EffectsSettings())
        override fun observe(): Flow<EffectsSettings> = settings
        override suspend fun current() = settings.value
        override suspend fun save(settings: EffectsSettings) { this.settings.value = settings }
    }
}
