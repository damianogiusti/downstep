package com.damianogiusti.slowdowner.domain.practice

import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

// Engine calls must stay on the caller's (main) thread: read before switching dispatcher.

internal fun PlaybackEngine.readPracticeSettings() = PracticeSettings(
    speedPercent = speedPercent,
    pitchSemitones = pitchSemitones,
    loop = LoopRegion(loopStart, loopEnd, isLoopOn),
)

internal fun PlaybackEngine.applyPracticeSettings(settings: PracticeSettings) {
    setSpeedPercent(settings.speedPercent)
    setPitchSemitones(settings.pitchSemitones)
    setLoop(settings.loop)
}
