package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine
import com.damianogiusti.slowdowner.domain.playback.PlaybackEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

/** Saves the practice state shortly after the user stops changing it. Never returns. */
class AutoSavePracticeStateUseCase(
    private val engine: PlaybackEngine,
    private val savePracticeState: SavePracticeStateUseCase,
) {
    @OptIn(FlowPreview::class)
    suspend operator fun invoke() {
        engine.events
            .filter { it == PlaybackEvent.PracticeSettingsChanged }
            // Slider drags fire many changes: write once they settle.
            .debounce(DEBOUNCE_MS)
            .collect { savePracticeState() }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
