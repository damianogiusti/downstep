package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import kotlinx.coroutines.flow.first

/**
 * Saves the level of a stem and, when this track is the open one and plays as stems, applies it at once.
 * What the mixer gets is the effective gain: a muted stem, or one silenced by another's solo, stays silent
 * while its level moves. Other tracks, and the original file of this one, only get it saved: it is applied
 * the next time they open as stems.
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
        val level = gain.coerceIn(0f, 1f)
        // The mute and solo are read only to know what to apply: a level alone does not say what plays.
        if (isPlayingStemsOf(nowPlaying, audioSources, trackId, stem)) {
            val mix = repository.observe(trackId).first()?.mix
            if (mix != null) mixer.setGain(stem, mix.withGain(stem, level).effectiveGain(stem))
        }
        repository.setGain(trackId, stem, level)
    }
}

/** The track is the open one and plays as stems that include [stem]. */
internal fun isPlayingStemsOf(
    nowPlaying: NowPlayingRepository,
    audioSources: AudioSourceRepository,
    trackId: String,
    stem: StemKind,
): Boolean {
    if (nowPlaying.observe().value?.id != trackId) return false
    val source = audioSources.current() as? AudioSource.Stems ?: return false
    return source.stems.any { it.kind == stem }
}
