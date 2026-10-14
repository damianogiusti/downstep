package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** Saves the current speed, pitch and loop of the open track under a name. */
class SavePresetUseCase(
    private val engine: PlaybackEngine,
    private val nowPlaying: NowPlayingRepository,
    private val repository: PracticeRepository,
) {
    suspend operator fun invoke(name: String): SavePresetResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return SavePresetResult.InvalidName
        val track = nowPlaying.observe().value ?: return SavePresetResult.NothingOpen
        return repository.savePreset(track.id, trimmed, engine.readPracticeSettings())
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
