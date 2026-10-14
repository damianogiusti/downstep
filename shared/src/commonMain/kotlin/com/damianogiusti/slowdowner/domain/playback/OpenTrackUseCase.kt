package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository

class OpenTrackUseCase(
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    operator fun invoke(track: LibraryTrack): Boolean {
        val opened = audioSources.open(AudioSource.LocalFile(track.filePath))
        nowPlaying.set(if (opened) track else null)
        return opened
    }
}
