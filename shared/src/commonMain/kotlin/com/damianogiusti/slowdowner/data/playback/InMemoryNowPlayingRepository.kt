package com.damianogiusti.slowdowner.data.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-scoped, like the native engine it mirrors. */
class InMemoryNowPlayingRepository : NowPlayingRepository {
    private val track = MutableStateFlow<LibraryTrack?>(null)

    override fun observe(): StateFlow<LibraryTrack?> = track.asStateFlow()

    override fun set(track: LibraryTrack?) {
        this.track.value = track
    }
}
