package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** Remembers the current speed, pitch and loop for the open library track. */
class SavePracticeStateUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val repository: PracticeRepository,
) {
    suspend operator fun invoke() {
        // Tracks opened outside the library (Spotify) have nothing to save against.
        val track = nowPlaying.observe().value ?: return
        if (!engine.isOpen) return
        repository.saveLastState(track.id, engine.readPracticeSettings())
    }
}
