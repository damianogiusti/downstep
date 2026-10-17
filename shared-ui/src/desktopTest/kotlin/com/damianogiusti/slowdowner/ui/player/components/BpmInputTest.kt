package com.damianogiusti.slowdowner.ui.player.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BpmInputTest {

    @Test
    fun `a whole tempo parses`() {
        assertEquals(92f, parseBpm("92"))
    }

    @Test
    fun `a decimal point or a decimal comma both parse`() {
        assertEquals(92.5f, parseBpm("92.5"))
        assertEquals(92.5f, parseBpm("92,5"))
    }

    @Test
    fun `an empty or unfinished number is not a tempo yet`() {
        assertNull(parseBpm(""))
        assertNull(parseBpm("."))
        assertNull(parseBpm("9.2.1"))
    }

    @Test
    fun `out of range values still parse, the dialog and the ViewModel reject them`() {
        assertEquals(1000f, parseBpm("1000"))
    }

    @Test
    fun `the field starts with the typed tempo, else the detected one`() {
        assertEquals("96", bpmPrefill(96f, 119.73f))
        assertEquals("119.7", bpmPrefill(null, 119.73f))
        assertEquals("", bpmPrefill(null, null))
    }

    @Test
    fun `when the detected tempo is saved untouched then nothing changes`() {
        // Saving "119.7" would make 119.73 a typed tempo, a tenth off the music.
        assertTrue(isPrefilledBpm(null, 119.73f, "119.7"))
        assertTrue(isPrefilledBpm(96.25f, 120f, bpmPrefill(96.25f, 120f)), "a typed tempo is not rounded again either")
        assertFalse(isPrefilledBpm(null, 119.73f, "119.8"))
        assertFalse(isPrefilledBpm(null, 119.73f, "120"))
        assertFalse(isPrefilledBpm(null, null, ""))
    }
}
