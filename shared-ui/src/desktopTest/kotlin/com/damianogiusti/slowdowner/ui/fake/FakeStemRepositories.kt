package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.stems.ModelDownloadResult
import com.damianogiusti.slowdowner.domain.stems.PlayableStem
import com.damianogiusti.slowdowner.domain.stems.PlayableStems
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMix
import com.damianogiusti.slowdowner.domain.stems.StemMixer
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.StemSeparationRepository
import com.damianogiusti.slowdowner.domain.stems.StemsRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.domain.stems.TrackStems
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield

/** A mixer for tests that never open stems. */
object NoStemMixer : StemMixer {
    override fun setGain(stem: StemKind, gain: Float) = Unit
}

/** A mixer that keeps what it is told, for tests of the live gain. */
class RecordingStemMixer : StemMixer {
    val calls = mutableListOf<Pair<StemKind, Float>>()

    override fun setGain(stem: StemKind, gain: Float) {
        calls += stem to gain
    }
}

/**
 * Keeps the ids it is given, the stems of the tracks in [tracks] (with a mode and gains, as the database
 * does) and records the deletes. [separatedIds] and [tracks] are independent: a test sets the one it reads.
 */
class FakeStemsRepository : StemsRepository {
    val separatedIds = MutableStateFlow<Set<String>>(emptySet())
    val tracks = MutableStateFlow<Map<String, TrackStems>>(emptyMap())
    val deleted = mutableListOf<String>()

    /** When set, a save waits for it: the database answering late, held by the test. */
    var saveGate: CompletableDeferred<Unit>? = null

    /** Every gain saved, in order. */
    val savedGains = mutableListOf<Triple<String, StemKind, Float>>()

    fun add(trackId: String, mode: TrackMode, gains: Map<StemKind, Float>) {
        tracks.value += trackId to TrackStems(trackId, "model", mode, StemMix(gains))
    }

    override fun observe(trackId: String): Flow<TrackStems?> = tracks.map { it[trackId] }

    override suspend fun playableStems(trackId: String): PlayableStems? {
        val stems = tracks.value[trackId] ?: return null
        return PlayableStems(stems.mode, stems.mix.gains.map { (kind, gain) -> PlayableStem(kind, "/stems/$trackId/$kind.wav", gain) })
    }

    override fun observeSeparatedTrackIds(): Flow<Set<String>> = separatedIds

    override suspend fun setMode(trackId: String, mode: TrackMode) {
        tracks.update { all -> all[trackId]?.let { all + (trackId to it.copy(mode = mode)) } ?: all }
    }

    override suspend fun setGain(trackId: String, stem: StemKind, gain: Float) {
        saveGate?.await()
        savedGains += Triple(trackId, stem, gain)
        tracks.update { all ->
            val current = all[trackId] ?: return@update all
            all + (trackId to current.copy(mix = StemMix(current.mix.gains + (stem to gain))))
        }
    }

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
