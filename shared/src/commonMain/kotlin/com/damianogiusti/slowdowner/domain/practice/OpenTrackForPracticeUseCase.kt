package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.OpenTrackUseCase
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/**
 * Opens a track with its own practice settings: saves the outgoing track, opens the new
 * one, then applies its last state, or the defaults if it has none.
 */
class OpenTrackForPracticeUseCase(
    private val openTrack: OpenTrackUseCase,
    private val savePracticeState: SavePracticeStateUseCase,
    private val engine: PlaybackEngine,
    private val repository: PracticeRepository,
) {
    suspend operator fun invoke(track: LibraryTrack): Boolean {
        savePracticeState()
        // Load before opening, so marking the track as playing and applying its settings run back to
        // back on the caller's (main) thread, with no suspension between them: a debounced auto-save
        // cannot slip in and store the old values on the new track. Only the decode runs elsewhere.
        val settings = repository.lastState(track.id) ?: PracticeSettings()
        val opened = openTrack(track)
        if (opened) engine.applyPracticeSettings(settings)
        return opened
    }
}
