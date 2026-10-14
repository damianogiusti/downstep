package com.damianogiusti.slowdowner.domain.playback

/** Loop points and play position, as fractions 0..1 of the file. */
data class LoopState(val start: Float, val end: Float, val isOn: Boolean, val position: Float)

/**
 * The loop buttons (A, B, half, double, shift), as pure functions. The Android engine runs them
 * natively; other engines use these. A loop is never shorter than [LoopRegion.MIN_LENGTH_SECONDS].
 * Functions return null when the action does not apply (for example doubling past the end).
 */
object LoopEditing {
    /** A point is set slightly before the press: the ear hears the beat a little after it. */
    const val BUTTON_DELAY_SECONDS = 0.05f

    /** A: the loop starts here. Turns the loop on, from here to the end if it was off. */
    fun markStart(state: LoopState, durationSeconds: Float, delaySeconds: Float = BUTTON_DELAY_SECONDS): LoopState {
        val end = if (state.isOn) state.end else 1f
        val latestStart = end - minLength(durationSeconds)
        val start = (state.position - delaySeconds / durationSeconds)
            .coerceAtMost(latestStart)
            .coerceAtLeast(0f)
        return state.copy(start = start, end = end, isOn = true)
    }

    /** B: the loop ends here and playback goes back to A. */
    fun markEnd(state: LoopState, durationSeconds: Float, delaySeconds: Float = BUTTON_DELAY_SECONDS): LoopState {
        var end = state.position - delaySeconds / durationSeconds
        if (end <= state.start + minLength(durationSeconds)) {
            end = (state.start + minLength(durationSeconds)).coerceAtMost(1f)
        }
        return state.copy(end = end, isOn = true, position = state.start)
    }

    fun halve(state: LoopState, durationSeconds: Float): LoopState? {
        val half = (state.end - state.start) / 2
        // Tolerance, so that a 2 s loop can still be halved despite rounding.
        if (half * durationSeconds < LoopRegion.MIN_LENGTH_SECONDS - 0.001f) return null
        val end = state.start + half
        return state.copy(end = end, position = if (state.position > end) state.start else state.position)
    }

    fun double(state: LoopState): LoopState? {
        val end = state.start + (state.end - state.start) * 2
        return if (end <= 1f) state.copy(end = end) else null
    }

    fun shiftForward(state: LoopState): LoopState? {
        val length = state.end - state.start
        var end = state.end + length
        if (end > 1f && end < 1f + ROUNDING) end = 1f
        if (end > 1f) return null
        val start = state.start + length
        return state.copy(start = start, end = end, position = start)
    }

    fun shiftBackward(state: LoopState): LoopState? {
        val length = state.end - state.start
        var start = state.start - length
        if (start < 0f && start > -ROUNDING) start = 0f
        if (start < 0f) return null
        return state.copy(start = start, end = state.end - length, position = start)
    }

    private const val ROUNDING = 1e-5f

    private fun minLength(durationSeconds: Float) =
        if (durationSeconds > 0f) (LoopRegion.MIN_LENGTH_SECONDS / durationSeconds).coerceAtMost(1f) else 0f
}
