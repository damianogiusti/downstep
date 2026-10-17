package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.effects.ActiveEq
import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** Saves the current speed, pitch, loop and EQ of the open track under a name. */
class SavePresetUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val repository: PracticeRepository,
    private val activeEq: ActiveEq,
) {
    suspend operator fun invoke(name: String): SavePresetResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return SavePresetResult.InvalidName
        val track = nowPlaying.observe().value ?: return SavePresetResult.NothingOpen
        // The user's own curve, not what the engine plays: Isolate is not part of a preset.
        return repository.savePreset(track.id, trimmed, engine.readPracticeSettings(), activeEq.curveOf(track.id))
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
