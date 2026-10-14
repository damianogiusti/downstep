package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.flow.StateFlow

class ObserveNowPlayingUseCase(private val nowPlaying: NowPlayingRepository) {
    operator fun invoke(): StateFlow<LibraryTrack?> = nowPlaying.observe()
}
