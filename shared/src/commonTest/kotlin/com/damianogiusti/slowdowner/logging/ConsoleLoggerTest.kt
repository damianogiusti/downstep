package com.damianogiusti.slowdowner.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsoleLoggerTest {

    private val lines = mutableListOf<String>()
    private val logger = ConsoleLogger(print = { lines += it })

    @Test
    fun `a line carries level and message and properties`() {
        logger.i("Export finished", mapOf("format" to "mp3"))

        assertEquals(listOf("I/Export finished {format=mp3}"), lines)
    }

    @Test
    fun `an error carries the stack trace`() {
        logger.e("Restore failed", IllegalStateException("broken"))

        val line = lines.single()
        assertTrue(line.startsWith("E/Restore failed\n"))
        // java.lang. on the JVM, kotlin. on iOS.
        assertTrue("IllegalStateException: broken" in line)
    }
}
