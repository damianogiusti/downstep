package com.damianogiusti.slowdowner.domain.playback

sealed interface PlaybackEvent {
    data object ControlsChanged : PlaybackEvent
    data object PlayStateChanged : PlaybackEvent
    data object PositionChanged : PlaybackEvent
    data object LoopChanged : PlaybackEvent
    data object Stopped : PlaybackEvent
    data object ReachedEnd : PlaybackEvent
    /** Speed, pitch or loop changed: the per-track practice state needs saving. */
    data object PracticeSettingsChanged : PlaybackEvent
}
