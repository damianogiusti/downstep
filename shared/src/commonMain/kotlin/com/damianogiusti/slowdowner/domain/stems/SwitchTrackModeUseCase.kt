package com.damianogiusti.slowdowner.domain.stems

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.practice.OpenTrackForPracticeUseCase
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.AudioSourceRepository
import com.damianogiusti.slowdowner.domain.source.OpenResult

/**
 * Changes what a track plays, original or stems, also while it is open and playing. The mode is always
 * saved. If the track is the open one and the source has to change, it is reopened the way a track is
 * opened for practice, so speed, pitch and loop come back from the state saved a moment before, then
 * the position and the play state are restored.
 */
class SwitchTrackModeUseCase(
    private val stems: StemsRepository,
    private val nowPlaying: NowPlayingRepository,
    private val audioSources: AudioSourceRepository,
    private val engine: PlaybackEngine,
    private val openTrack: OpenTrackForPracticeUseCase,
) {
    /**
     * The result of the reopen, or null when nothing was reopened: the track is not the open one, or the
     * open source already is what [mode] asks for, or the track has no stems to switch to.
     */
    suspend operator fun invoke(trackId: String, mode: TrackMode): OpenResult? {
        stems.setMode(trackId, mode)
        val track = nowPlaying.observe().value?.takeIf { it.id == trackId } ?: return null
        if (!engine.isOpen) return null
        val isPlayingStems = audioSources.current() is AudioSource.Stems
        val canPlayStems = mode == TrackMode.STEMS && stems.playableStems(trackId) != null
        if (isPlayingStems == canPlayStems) return null

        // Both files are as long as the original: the same fraction is the same moment of the song.
        val position = engine.position
        val wasPlaying = engine.isPlaying
        val result = openTrack(track)
        if (result != OpenResult.Opened) return result
        engine.seekTo(position)
        if (wasPlaying && !engine.isPlaying) engine.togglePlayStop()
        return result
    }
}
