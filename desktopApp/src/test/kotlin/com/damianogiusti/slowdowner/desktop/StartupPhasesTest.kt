package com.damianogiusti.slowdowner.desktop

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StartupPhasesTest {

    private val process = Instant.parse("2026-11-25T10:00:00Z")

    @Test
    fun `each phase falls in its bucket`() {
        val marks = StartupMarks(
            process = process,
            main = process.plusMillis(1_200),
            locked = process.plusMillis(1_300),
            appStarted = process.plusMillis(4_300),
        )

        val properties = startupProperties(marks, windowShown = process.plusMillis(11_000), isSplashShown = true)

        assertEquals(
            mapOf(
                "startup" to "10-20s",
                "jvm" to "1-2s",
                "lock" to "<0.5s",
                "init" to "3-5s",
                "ui" to "5-10s",
                "splash" to "shown",
            ),
            properties,
        )
    }

    @Test
    fun `when the process start is unknown then jvm is left out`() {
        val marks = StartupMarks(process = null, main = process, locked = process, appStarted = process)

        val properties = startupProperties(marks, windowShown = process, isSplashShown = false)

        assertEquals(setOf("startup", "lock", "init", "ui", "splash"), properties.keys)
        assertEquals("none", properties["splash"])
    }

    @Test
    fun `a duration falls in a fine bucket`() {
        assertEquals(
            listOf("<0.5s", "0.5-1s", "1-2s", "2-3s", "3-5s", "5-10s", "10-20s", "20-40s", ">40s", ">40s"),
            listOf(0L, 500L, 1_999L, 2_000L, 4_999L, 5_000L, 19_999L, 20_000L, 40_000L, 300_000L)
                .map { phaseBucket(Duration.ofMillis(it)) },
        )
    }

    @Test
    fun `a clock that goes back gives the shortest bucket`() {
        assertEquals("<0.5s", phaseBucket(Duration.ofMillis(-200)))
    }
}
