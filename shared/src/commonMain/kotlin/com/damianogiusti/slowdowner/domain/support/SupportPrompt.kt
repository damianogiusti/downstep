package com.damianogiusti.slowdowner.domain.support

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What the app remembers about the "Downstep is free" prompt. */
data class SupportPromptHistory(
    val launchCount: Int = 0,
    val lastShownAt: Instant? = null,
)

/** The prompt to show now. Close stays disabled for [closeDelay]. */
data class SupportPrompt(val closeDelay: Duration)

internal object SupportPromptRules {
    // Not at the first launch: by the third one the app has already been useful.
    const val FIRST_LAUNCH = 3
    val INTERVAL = 30.days
    // Only the first time: enough to read two lines, not a punishment.
    val FIRST_CLOSE_DELAY = 5.seconds

    fun promptFor(history: SupportPromptHistory, now: Instant): SupportPrompt? {
        if (history.launchCount < FIRST_LAUNCH) return null
        val lastShownAt = history.lastShownAt ?: return SupportPrompt(FIRST_CLOSE_DELAY)
        return if (now - lastShownAt >= INTERVAL) SupportPrompt(Duration.ZERO) else null
    }
}
