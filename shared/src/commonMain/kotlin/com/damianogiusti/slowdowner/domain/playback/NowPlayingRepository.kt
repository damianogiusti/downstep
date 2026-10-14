package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.flow.StateFlow

/** The track loaded in the engine; outlives any screen. */
interface NowPlayingRepository {
    fun observe(): StateFlow<LibraryTrack?>
    fun set(track: LibraryTrack?)
}
