package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import kotlinx.coroutines.flow.first

/**
 * Saves the solo of a stem and, when this track is the open one and plays as stems, applies the effective gain of
 * every stem at once: a solo changes what the others play. Does nothing when the track is not separated or lacks
 * the stem.
 */
class SetStemSoloedUseCase(
    private val repository: StemsRepository,
    private val mixer: StemMixer,
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(trackId: String, stem: StemKind, isSoloed: Boolean) {
        val mix = repository.observe(trackId).first()?.mix?.takeIf { stem in it.gains } ?: return
        if (isPlayingStemsOf(nowPlaying, audioSources, trackId, stem)) mixer.applyMix(mix.withSoloed(stem, isSoloed))
        repository.setSoloed(trackId, stem, isSoloed)
    }
}
