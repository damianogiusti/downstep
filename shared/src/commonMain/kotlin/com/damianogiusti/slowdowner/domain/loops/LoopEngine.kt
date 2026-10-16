package com.damianogiusti.slowdowner.domain.loops

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import kotlin.math.abs

// The engine plays the active loop of the open track: loop_start/loop_end of the practice state
// mirror it, so presets and the restore on open keep working on a single region. Engine calls stay
// on the caller's (main) thread: repositories switch dispatcher and come back.

/** True when [trackId] is the track loaded in the engine. */
internal fun PlaybackEngine.isPlayingTrack(trackId: String, nowPlaying: NowPlayingRepository): Boolean =
    isOpen && nowPlaying.observe().value?.id == trackId

internal suspend fun LoopRepository.isActive(loop: Loop): Boolean = trackLoops(loop.trackId).activeLoopId == loop.id

internal fun Loop.toRegion(isOn: Boolean) = LoopRegion(start, end, isOn)

/** What the engine loops for this track: the active loop, or nothing (the whole file, off) without one. */
internal fun TrackLoops.activeLoopRegion(isOn: Boolean): LoopRegion = activeLoop?.toRegion(isOn) ?: LoopRegion.WHOLE_FILE

/** Progression drives the speed: only touch the engine when the value really changes. */
internal fun PlaybackEngine.applyProgressionSpeed(loop: Loop) {
    if (!loop.progression.enabled) return
    val target = loop.progression.speedAfter(loop.passes)
        .coerceIn(PlaybackEngine.MIN_SPEED_PERCENT, PlaybackEngine.MAX_SPEED_PERCENT)
    if (abs(speedPercent - target) > SPEED_TOLERANCE) setSpeedPercent(target)
}

private const val SPEED_TOLERANCE = 0.01f
