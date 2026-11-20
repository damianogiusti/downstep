package com.damianogiusti.slowdowner.domain.export

/** The part of the open file to render, as fractions 0..1 of the whole file, [start] < [end]. */
data class RenderRange(val start: Float, val end: Float) {
    init {
        require(start >= 0f && end <= 1f && start < end) { "Invalid range $start..$end" }
    }

    companion object {
        val WHOLE_FILE = RenderRange(0f, 1f)
    }
}
