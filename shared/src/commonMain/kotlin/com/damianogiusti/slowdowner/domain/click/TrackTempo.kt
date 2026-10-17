package com.damianogiusti.slowdowner.domain.click

import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.loops.TrackLoops

/**
 * A track's beat grid at 100% speed: beats at [beatOffsetSeconds] + k × 60 / [bpm] seconds of the
 * file, the first of every [beatsPerBar] accented. Bars are not detected: beat 0 is the first beat.
 */
data class TrackTempo(
    val bpm: Float,
    val beatOffsetSeconds: Float = 0f,
    val beatsPerBar: Int = BeatGrid.BEATS_PER_BAR,
)

/** The grid the metronome clicks on: the effective BPM (typed, else detected) and its first beat. */
fun TrackLoops.tempo(): TrackTempo? =
    effectiveBpm?.takeIf(BeatGrid::isValidBpm)?.let { TrackTempo(it, beatOffsetSeconds?.takeIf { o -> o.isFinite() } ?: 0f) }
