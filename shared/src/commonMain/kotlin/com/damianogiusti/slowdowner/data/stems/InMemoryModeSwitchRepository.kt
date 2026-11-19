package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.ModeSwitchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-scoped, like the engine whose reopen it tells. */
class InMemoryModeSwitchRepository : ModeSwitchRepository {
    private val isSwitching = MutableStateFlow(false)

    override fun observe(): StateFlow<Boolean> = isSwitching.asStateFlow()

    override fun set(isSwitching: Boolean) {
        this.isSwitching.value = isSwitching
    }
}
