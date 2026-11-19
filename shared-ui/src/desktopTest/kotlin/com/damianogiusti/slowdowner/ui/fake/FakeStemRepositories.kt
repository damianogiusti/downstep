package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.StemSeparationRepository
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield

/** Keeps the ids it is given and records the deletes. */
class FakeStemsRepository : StemsRepository {
    val separatedIds = MutableStateFlow<Set<String>>(emptySet())
    val deleted = mutableListOf<String>()

    override fun observe(trackId: String): Flow<TrackStems?> = flowOf(null)
    override fun observeSeparatedTrackIds(): Flow<Set<String>> = separatedIds
    override suspend fun setMode(trackId: String, mode: TrackMode) = Unit
    override suspend fun setGain(trackId: String, stem: StemKind, gain: Float) = Unit
    override suspend fun delete(trackId: String) {
        deleted += trackId
        separatedIds.value -= trackId
    }
}

/** The model is in [state]; a download reports [progressSteps], waits for [gate] if set, then gives [downloadResult]. */
class FakeStemModelRepository(initial: StemModelState = StemModelState.Ready) : StemModelRepository {
    val state = MutableStateFlow(initial)
    var downloadResult: ModelDownloadResult = ModelDownloadResult.Success
    var progressSteps: List<Float> = emptyList()
    var gate: CompletableDeferred<Unit>? = null
    var downloads = 0

    override fun observe(): Flow<StemModelState> = state

    override suspend fun download(onProgress: (Float) -> Unit): ModelDownloadResult {
        downloads++
        for (step in progressSteps) {
            onProgress(step)
            // Lets a collector see each step, as a download that takes time would.
            yield()
        }
        gate?.await()
        if (downloadResult == ModelDownloadResult.Success) state.value = StemModelState.Ready
        return downloadResult
    }

    override suspend fun delete() {
        state.value = StemModelState.NotDownloaded
    }
}

/** Starts as [startResult] says; a [SeparationStart.Started] makes the job run until the test finishes it. */
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
        val running = state.value as? SeparationJobState.Running ?: return
        state.value = SeparationJobState.Finished(running.trackId, SeparationResult.Cancelled)
    }

    override suspend fun acknowledge() {
        acknowledges++
        if (state.value is SeparationJobState.Finished) state.value = SeparationJobState.Idle
    }
}
