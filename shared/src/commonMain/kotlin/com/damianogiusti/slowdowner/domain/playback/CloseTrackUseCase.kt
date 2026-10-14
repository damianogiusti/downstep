package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository

class CloseTrackUseCase(
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    operator fun invoke() {
        audioSources.close()
        nowPlaying.set(null)
    }
}
