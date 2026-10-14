package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

class ApplyPresetUseCase(private val engine: PlaybackEngine) {
    operator fun invoke(preset: PracticePreset) = engine.applyPracticeSettings(preset.settings)
}
