package com.damianogiusti.slowdowner.ui.player.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
