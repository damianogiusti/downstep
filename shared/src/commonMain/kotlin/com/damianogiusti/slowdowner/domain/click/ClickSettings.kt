package com.damianogiusti.slowdowner.domain.click

/**
 * The metronome and count-in choices: global, not per track (the tempo is per track), and kept
 * across launches.
 */
data class ClickSettings(
    val isMetronomeOn: Boolean = false,
    /** 0..1, the click's own level; the count-in uses it too. */
    val volume: Float = DEFAULT_VOLUME,
    val isCountInOn: Boolean = false,
    /** Clicks before playback starts: one bar of 4/4 by default. */
    val countInBeats: Int = DEFAULT_COUNT_IN_BEATS,
    /** Also count in before each restart of the loop. */
    val countInEveryLoop: Boolean = false,
) {
    /** Within range: a stored value from a newer or broken app never reaches the engine as is. */
    fun normalized(): ClickSettings = copy(
        volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else DEFAULT_VOLUME,
        countInBeats = countInBeats.coerceIn(MIN_COUNT_IN_BEATS, MAX_COUNT_IN_BEATS),
    )

    companion object {
        const val DEFAULT_VOLUME = 0.7f
        const val DEFAULT_COUNT_IN_BEATS = 4
        const val MIN_COUNT_IN_BEATS = 1
        const val MAX_COUNT_IN_BEATS = 16
        /** The count-in lengths the menu offers: half a bar to two bars of 4/4. */
        val COUNT_IN_CHOICES = listOf(2, 4, 8)
    }
}
