package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.stems.SeparationOutcome
import com.damianogiusti.slowdowner.data.stems.SeparationRequest
import com.damianogiusti.slowdowner.data.stems.StemSeparatorDataSource

/** Runs [behavior] as the separation: it can report progress, wait, fail, or throw. */
class FakeStemSeparatorDataSource(
    var behavior: suspend (SeparationRequest, (Float) -> Unit) -> SeparationOutcome,
) : StemSeparatorDataSource {

    val requests = mutableListOf<SeparationRequest>()

    override suspend fun separate(request: SeparationRequest, onProgress: (Float) -> Unit): SeparationOutcome {
        requests += request
        return behavior(request, onProgress)
    }
}
