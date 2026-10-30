package com.damianogiusti.slowdowner.domain.playback

sealed interface PlaybackEvent {
    data object ControlsChanged : PlaybackEvent
    data object PlayStateChanged : PlaybackEvent
    /** A seek by the user. Playback moving on its own is in [PlaybackEngine.positionFlow]. */
    data object PositionChanged : PlaybackEvent
    data object LoopChanged : PlaybackEvent
    data object Stopped : PlaybackEvent
    data object ReachedEnd : PlaybackEvent
    /** Speed, pitch or loop changed: the per-track practice state needs saving. */
    data object PracticeSettingsChanged : PlaybackEvent
}
