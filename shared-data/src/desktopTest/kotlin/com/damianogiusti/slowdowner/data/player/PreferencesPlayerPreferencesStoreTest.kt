package com.damianogiusti.slowdowner.data.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import java.util.prefs.Preferences

class PreferencesPlayerPreferencesStoreTest {

    // A throwaway node: never the real "com/damianogiusti/slowdowner/player".
    private val node = Preferences.userRoot().node("com/damianogiusti/slowdowner-test/${UUID.randomUUID()}")
    private val store = PreferencesPlayerPreferencesStore(node)

    @After
    fun tearDown() {
        node.removeNode()
        Preferences.userRoot().flush()
    }

    @Test
    fun `when nothing is written then read is null`() {
        assertNull(store.read())
    }

    @Test
    fun `when preferences are written then they are read back`() {
        store.write(PlayerPreferencesDto(isPitchFine = true))
        assertEquals(PlayerPreferencesDto(isPitchFine = true), store.read())

        store.write(PlayerPreferencesDto(isPitchFine = false))
        assertEquals(PlayerPreferencesDto(isPitchFine = false), store.read())
    }

    @Test
    fun `each choice is stored on its own`() {
        val written = PlayerPreferencesDto(isPitchFine = false, isLoopFine = true, isWaveformShown = true)

        store.write(written)

        assertEquals(written, store.read())
    }

    @Test
    fun `a store written before the loop and waveform choices reads them as off`() {
        node.putBoolean("pitch_fine", true)

        assertEquals(PlayerPreferencesDto(isPitchFine = true, isLoopFine = false, isWaveformShown = false), store.read())
    }
}
