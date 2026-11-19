package com.damianogiusti.slowdowner.domain.export

import kotlin.math.roundToInt

/** Loop points as text for titles and file names. */
internal object LoopClock {
    /** "0:42–1:13", for a track title. */
    fun span(startSeconds: Float, endSeconds: Float): String = "${clock(startSeconds)}–${clock(endSeconds)}"

    /** "0m42s-1m13s": no `:`, which file systems refuse. */
    fun fileSpan(startSeconds: Float, endSeconds: Float): String {
        fun part(seconds: Float) = wholeSeconds(seconds).let { "${it / 60}m${(it % 60).toString().padStart(2, '0')}s" }
        return "${part(startSeconds)}-${part(endSeconds)}"
    }

    private fun clock(seconds: Float) = wholeSeconds(seconds).let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }

    private fun wholeSeconds(seconds: Float) = seconds.coerceAtLeast(0f).roundToInt()
}
