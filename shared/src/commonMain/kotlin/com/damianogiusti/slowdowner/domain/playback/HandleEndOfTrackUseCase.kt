package com.damianogiusti.slowdowner.domain.playback

/** At the end of the file, stop and get ready to play the same passage again. */
class HandleEndOfTrackUseCase(private val engine: PlaybackEngine) {
    operator fun invoke() {
        engine.stop()
        engine.seekTo(if (engine.isLoopOn) engine.loopStart else 0f)
    }
}
