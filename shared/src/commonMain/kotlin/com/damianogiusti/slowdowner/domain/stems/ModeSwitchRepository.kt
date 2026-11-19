package com.damianogiusti.slowdowner.domain.stems

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the open track is being reopened to change its mode ([SwitchTrackModeUseCase]). While it is true,
 * [com.damianogiusti.slowdowner.domain.playback.NowPlayingRepository] is null and the engine is closed for the
 * decode, but it is the same track coming back: the player keeps showing it instead of its empty state.
 */
interface ModeSwitchRepository {
    fun observe(): StateFlow<Boolean>
    fun set(isSwitching: Boolean)
}
