package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** Sets an active A/B loop, for example from handles dragged on the timeline. */
class SetLoopRegionUseCase(private val engine: PlaybackEngine) {

    operator fun invoke(start: Float, end: Float) {
        engine.setLoop(LoopRegion.between(start, end, engine.durationSeconds))
    }
}
