package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StemSeparationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStemSeparationRepository : StemSeparationRepository {

    val state = MutableStateFlow<SeparationJobState>(SeparationJobState.Idle)
    var startResult: SeparationStart = SeparationStart.Started
    val started = mutableListOf<String>()
    var cancels = 0
    var acknowledges = 0

    override fun observe(): Flow<SeparationJobState> = state

    override suspend fun start(trackId: String): SeparationStart {
        started += trackId
        if (startResult == SeparationStart.Started) state.value = SeparationJobState.Running(trackId, 0f)
        return startResult
    }

    override suspend fun cancel() {
        cancels++
    }

    override suspend fun acknowledge() {
        acknowledges++
    }
}
