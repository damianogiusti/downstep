package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** The title offered for a track made from the loop: "Title (loop 0:42–1:13)". Null without a loop. */
class SuggestLoopTrackTitleUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
) {
    operator fun invoke(): String? {
        val track = nowPlaying.observe().value ?: return null
        if (!engine.isOpen || !engine.hasRenderableLoop()) return null
        val duration = engine.durationSeconds
        return "${track.title} (loop ${LoopClock.span(engine.loopStart * duration, engine.loopEnd * duration)})"
    }
}
