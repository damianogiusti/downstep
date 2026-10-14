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

/** The prompt to show now. [isFirst]: never closed before, so Close waits for [closeDelay]. */
data class SupportPrompt(val isFirst: Boolean) {
    val closeDelay: Duration get() = if (isFirst) SupportPromptRules.FIRST_CLOSE_DELAY else Duration.ZERO
}

/** How the prompt was closed. "Why is it free?" is not one: it keeps the prompt open. */
enum class SupportPromptAction(val logName: String) {
    DONATE("donate"),
    CLOSE("close"),
    /** Esc or a click outside. */
    DISMISS("dismiss"),
}

internal object SupportPromptRules {
    // Not at the first launch: by the third one the app has already been useful.
    const val FIRST_LAUNCH = 3
    val INTERVAL = 30.days
    // Only the first time: enough to read two lines, not a punishment.
    val FIRST_CLOSE_DELAY = 5.seconds

    fun promptFor(history: SupportPromptHistory, now: Instant): SupportPrompt? {
        if (history.launchCount < FIRST_LAUNCH) return null
        val lastShownAt = history.lastShownAt ?: return SupportPrompt(isFirst = true)
        return if (now - lastShownAt >= INTERVAL) SupportPrompt(isFirst = false) else null
    }

    fun occurrence(isFirst: Boolean) = if (isFirst) "first" else "repeat"
}
