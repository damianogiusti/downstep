package com.damianogiusti.slowdowner.desktop

import java.time.Duration
import java.time.Instant

/**
 * When each step of a start was reached, to tell where a slow start spends its time. The time before
 * the process exists (Explorer, an antivirus scanning the launcher) is out of reach.
 *
 * - jvm: process start to main(): the JVM and the launcher.
 * - lock: main() to the single-instance lock: the update check, and up to 20 s of waiting for another copy.
 * - init: the lock to "App started": log, crash reporting, audio engine.
 * - ui: "App started" to the window: Koin, the app's work, Compose and Skia.
 */
internal data class StartupMarks(
    val process: Instant?,
    val main: Instant,
    val locked: Instant,
    val appStarted: Instant,
)

/** The properties of "Window shown": the coarse total kept since 1.3.0, then one bucket per phase. */
internal fun startupProperties(marks: StartupMarks, windowShown: Instant, isSplashShown: Boolean): Map<String, String> {
    val phases = mapOf(
        "lock" to phaseBucket(Duration.between(marks.main, marks.locked)),
        "init" to phaseBucket(Duration.between(marks.locked, marks.appStarted)),
        "ui" to phaseBucket(Duration.between(marks.appStarted, windowShown)),
        "splash" to if (isSplashShown) "shown" else "none",
    )
    val process = marks.process ?: return phases + ("startup" to startupBucket(0))
    return phases + mapOf(
        "startup" to startupBucket(Duration.between(process, windowShown).seconds.coerceAtLeast(0)),
        "jvm" to phaseBucket(Duration.between(process, marks.main)),
    )
}

/** Finer than [startupBucket], still few values: they become properties in Sentry. */
internal fun phaseBucket(duration: Duration): String {
    val millis = duration.toMillis()
    return PHASE_BUCKETS.firstOrNull { (limit, _) -> millis < limit }?.second ?: ">40s"
}

private val PHASE_BUCKETS = listOf(
    500L to "<0.5s",
    1_000L to "0.5-1s",
    2_000L to "1-2s",
    3_000L to "2-3s",
    5_000L to "3-5s",
    10_000L to "5-10s",
    20_000L to "10-20s",
    40_000L to "20-40s",
)

internal fun processStart(): Instant? = ProcessHandle.current().info().startInstant().orElse(null)
