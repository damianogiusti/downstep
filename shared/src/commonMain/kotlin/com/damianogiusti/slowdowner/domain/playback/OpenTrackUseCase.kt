package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult

/**
 * The repository decodes the whole file on its own dispatcher: seconds for a long song, which would
 * freeze the window on the main thread. This use case returns on the caller's dispatcher with
 * [NowPlayingRepository] already set, so the caller's next statement runs right after, with nothing
 * in between.
 */
class OpenTrackUseCase(
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(track: LibraryTrack): OpenResult {
        // The outgoing file is closed while the new one decodes: nothing is playing then. Saying so
        // also stops an auto-save from storing the half-opened engine against the outgoing track.
        nowPlaying.set(null)
        val result = audioSources.open(AudioSource.LocalFile(track.filePath))
        nowPlaying.set(if (result == OpenResult.Opened) track else null)
        return result
    }
}
