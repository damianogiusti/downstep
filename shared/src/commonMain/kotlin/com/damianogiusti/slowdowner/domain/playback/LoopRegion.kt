package com.damianogiusti.slowdowner.domain.playback

/** A/B loop points as fractions 0..1 of the whole file. */
data class LoopRegion(val start: Float, val end: Float, val isOn: Boolean) {

    companion object {
        val WHOLE_FILE = LoopRegion(start = 0f, end = 1f, isOn = false)

        /** Shorter loops are inaudible stutters. */
        const val MIN_LENGTH_SECONDS = 1f

        /**
         * An active loop between two points in any order, clamped to the file and at
         * least [MIN_LENGTH_SECONDS] long.
         */
        fun between(first: Float, second: Float, durationSeconds: Float): LoopRegion {
            val minLength = if (durationSeconds > 0f) (MIN_LENGTH_SECONDS / durationSeconds).coerceAtMost(1f) else 0f
            val start = minOf(first, second).coerceIn(0f, 1f - minLength)
            val end = maxOf(maxOf(first, second).coerceIn(0f, 1f), start + minLength)
            return LoopRegion(start, end, isOn = true)
        }
    }
}
