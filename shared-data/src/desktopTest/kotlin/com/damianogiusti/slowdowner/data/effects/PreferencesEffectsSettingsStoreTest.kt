package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.effects.ProcessingQuality
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import java.util.prefs.Preferences

/** On a throwaway node: never the developer's own preferences. */
class PreferencesEffectsSettingsStoreTest {

    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner/test/effects-${UUID.randomUUID()}")
    private val store = PreferencesEffectsSettingsStore(node)

    @After
    fun tearDown() = node.removeNode()

    @Test
    fun `when nothing is stored then nothing is read`() {
        assertNull(store.read())
    }

    @Test
    fun `when the first EQ's keys are there then they are read and migrated`() {
        node.putBoolean("eq_enabled", true)
        node.putFloat("eq_preamp_db", -3f)
        node.put("eq_bands_db", "3.0,1.5,0.0,-2.0,0.0,4.5,6.0")
        node.put("quality", "LOW")

        val settings = EffectsSettingsMapper.toDomain(store.read()!!)

        assertEquals(EqCurve.fromLegacyGains(true, -3f, listOf(3f, 1.5f, 0f, -2f, 0f, 4.5f, 6f)), settings.eq)
        assertEquals(ProcessingQuality.LOW, settings.quality)
    }

    @Test
    fun `when settings are written then the curve replaces the first EQ's keys`() {
        node.putBoolean("eq_enabled", true)
        node.putFloat("eq_preamp_db", -3f)
        node.put("eq_bands_db", "1,1,1,1,1,1,1")
        node.put("quality", "HIGH")
        val curve = EqCurve(enabled = true, preampDb = 1f, bands = listOf(EqBand(250f, -6f, 4f)))

        store.write(EffectsSettingsMapper.toDto(EffectsSettings(eq = curve, quality = ProcessingQuality.VERY_HIGH)))

        assertEquals(setOf("eq_curve", "quality"), node.keys().toSet())
        val read = EffectsSettingsMapper.toDomain(store.read()!!)
        assertEquals(EffectsSettings(eq = curve, quality = ProcessingQuality.VERY_HIGH), read)
    }

    @Test
    fun `when only the curve is stored then it is read`() {
        node.put("eq_curve", EqCurveCodec.encode(EqCurve(enabled = true, preampDb = 2f)))

        val settings = EffectsSettingsMapper.toDomain(store.read()!!)

        assertEquals(EqCurve(enabled = true, preampDb = 2f), settings.eq)
        assertEquals(ProcessingQuality.HIGH, settings.quality)
    }
}
