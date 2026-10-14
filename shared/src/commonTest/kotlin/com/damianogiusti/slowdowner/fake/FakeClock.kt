package com.damianogiusti.slowdowner.fake

import kotlin.time.Clock
import kotlin.time.Instant

class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
}
