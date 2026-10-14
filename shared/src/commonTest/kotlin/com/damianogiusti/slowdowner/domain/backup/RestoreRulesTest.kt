package com.damianogiusti.slowdowner.domain.backup

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class RestoreRulesTest {

    @Test
    fun `when the playlist name is free then it is kept`() {
        assertEquals("Jazz", RestoreRules.uniquePlaylistName("  Jazz ", setOf("Rock")))
    }

    @Test
    fun `when the playlist name is taken then the first free number is appended`() {
        assertEquals("Jazz (2)", RestoreRules.uniquePlaylistName("Jazz", setOf("Jazz")))
        assertEquals("Jazz (3)", RestoreRules.uniquePlaylistName("Jazz", setOf("Jazz", "Jazz (2)")))
    }

    @Test
    fun `when a long name gets a suffix then it stays within the limit`() {
        val long = "x".repeat(60)

        val name = RestoreRules.uniquePlaylistName(long, setOf(long))

        assertEquals(60, name.length)
        assertEquals("x".repeat(56) + " (2)", name)
    }

    @Test
    fun `when the playlist name is blank then a default name is used`() {
        assertEquals(RestoreRules.DEFAULT_PLAYLIST_NAME, RestoreRules.uniquePlaylistName("  ", emptySet()))
    }

    @Test
    fun `when a preset name is blank or too long then it is rejected`() {
        assertEquals("Solo", RestoreRules.presetName(" Solo "))
        assertNull(RestoreRules.presetName(" "))
        assertNull(RestoreRules.presetName("x".repeat(41)))
    }

    @Test
    fun `when settings are out of range then they are clamped`() {
        val settings = PracticeSettings(500f, -40f, LoopRegion(-1f, 2f, isOn = true))

        val sanitized = RestoreRules.sanitize(settings)

        assertEquals(PracticeSettings(200f, -12f, LoopRegion(0f, 1f, isOn = true)), sanitized)
    }

    @Test
    fun `when settings are not finite or the loop is reversed then defaults are used`() {
        val settings = PracticeSettings(Float.NaN, Float.POSITIVE_INFINITY, LoopRegion(0.6f, 0.2f, isOn = true))

        assertEquals(PracticeSettings(), RestoreRules.sanitize(settings))
    }

    @Test
    fun `when settings are valid then they are unchanged`() {
        val settings = PracticeSettings(75f, -2f, LoopRegion(0.2f, 0.4f, isOn = true))

        assertEquals(settings, RestoreRules.sanitize(settings))
    }
}
