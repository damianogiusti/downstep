package com.damianogiusti.slowdowner.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupBucketTest {
    @Test
    fun `startup time falls in a coarse bucket`() {
        assertEquals(
            listOf("<3s", "3-10s", "3-10s", "10-20s", "10-20s", ">20s"),
            listOf(0L, 3L, 9L, 10L, 20L, 21L).map(::startupBucket),
        )
    }
}
