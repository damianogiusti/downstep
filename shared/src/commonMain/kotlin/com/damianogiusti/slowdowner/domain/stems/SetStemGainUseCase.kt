package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository

/**
 * Saves the gain of a stem and, when this track is the open one and plays as stems, applies it at once.
 * Other tracks, and the original file of this one, only get it saved: it is applied the next time they open as stems.
 */
class SetStemGainUseCase(
    private val repository: StemsRepository,
    private val mixer: StemMixer,
    private val audioSources: AudioSourceRepository,
    private val nowPlaying: NowPlayingRepository,
) {
    suspend operator fun invoke(trackId: String, stem: StemKind, gain: Float) {
        // NaN compares false with everything: it would reach the engine.
        if (gain.isNaN()) return
        // First, so a slider drag is heard without waiting for the database.
        if (isPlayingStemsOf(trackId, stem)) mixer.setGain(stem, gain.coerceIn(0f, 1f))
        repository.setGain(trackId, stem, gain)
    }

    private fun isPlayingStemsOf(trackId: String, stem: StemKind): Boolean {
        if (nowPlaying.observe().value?.id != trackId) return false
        val source = audioSources.current() as? AudioSource.Stems ?: return false
        return source.stems.any { it.kind == stem }
    }
}
