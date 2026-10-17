package com.damianogiusti.slowdowner.domain.loops

import kotlin.test.Test
import kotlin.test.assertEquals

class LoopNameTest {

    @Test
    fun `when there are no loops then the first is Loop 1`() {
        assertEquals("Loop 1", Loop.nextDefaultName(emptyList()))
    }

    @Test
    fun `when loops have custom names then the number follows the count`() {
        val loops = listOf(loop(1, "Intro"), loop(2, "Verse"), loop(3, "Solo"), loop(4, "Outro"))

        assertEquals("Loop 5", Loop.nextDefaultName(loops))
    }

    @Test
    fun `when a numbered loop is above the count then the number follows it`() {
        assertEquals("Loop 10", Loop.nextDefaultName(listOf(loop(1, "Loop 9"))))
    }

    private fun loop(id: Long, name: String) = Loop(id, "a", name, 0f, 0.1f)
}
