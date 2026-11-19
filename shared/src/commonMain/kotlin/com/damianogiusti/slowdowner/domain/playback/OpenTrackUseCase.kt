package com.damianogiusti.slowdowner.domain.playback

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.source.StemSource
import com.damianogiusti.slowdowner.domain.stems.PlayableStems
import com.damianogiusti.slowdowner.domain.stems.StemMixer
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.logging.Logger

/**
 * The repository decodes the whole file on its own dispatcher: seconds for a long song, which would
 * freeze the window on the main thread. This use case returns on the caller's dispatcher with
 * [NowPlayingRepository] already set, so the caller's next statement runs right after, with nothing
 * in between.
 *
 * A track opens as its stems when it is separated and saved in [TrackMode.STEMS], else as the original file.
 * The stems are looked up before marking the track as playing, and their saved gains are applied right after
 * the mark: [StemMixer.setGain] does not suspend, so the invariant above holds with stems too.
 */
class OpenTrackUseCase(
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
    private val stems: StemsRepository,
    private val mixer: StemMixer,
    private val logger: Logger,
) {
    suspend operator fun invoke(track: LibraryTrack): OpenResult {
        // The outgoing file is closed while the new one decodes: nothing is playing then. Saying so
        // also stops an auto-save from storing the half-opened engine against the outgoing track.
        nowPlaying.set(null)
        val playable = stems.playableStems(track.id)?.takeIf { it.mode == TrackMode.STEMS }
        if (playable != null) {
            val opened = openStems(track, playable)
            if (opened) return OpenResult.Opened
        }
        val result = audioSources.open(AudioSource.LocalFile(track.filePath))
        nowPlaying.set(if (result == OpenResult.Opened) track else null)
        return result
    }

    private suspend fun openStems(track: LibraryTrack, playable: PlayableStems): Boolean {
        val source = AudioSource.Stems(playable.stems.map { StemSource(it.kind, it.path) })
        val result = audioSources.open(source)
        if (result != OpenResult.Opened) {
            // Back to the original, and the mode with it: otherwise every open of this track would try again.
            logger.w("Cannot open the stems, playing the original", properties = mapOf("reason" to if (result == OpenResult.TooLong) "tooLong" else "failed"))
            stems.setMode(track.id, TrackMode.FULL_TRACK)
            return false
        }
        nowPlaying.set(track)
        // No suspension from the mark above to here, nor to the caller's next statement.
        for (stem in playable.stems) mixer.setGain(stem.kind, stem.gain)
        return true
    }
}
