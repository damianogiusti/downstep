package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.database.isConstraintViolation
import com.damianogiusti.slowdowner.data.library.LibraryStore
import com.damianogiusti.slowdowner.data.logging.failure
import com.damianogiusti.slowdowner.domain.stems.SeparationFailureReason
import com.damianogiusti.slowdowner.domain.stems.SeparationJobState
import com.damianogiusti.slowdowner.domain.stems.SeparationResult
import com.damianogiusti.slowdowner.domain.stems.SeparationStart
import com.damianogiusti.slowdowner.domain.stems.StemModelRepository
import com.damianogiusti.slowdowner.domain.stems.StemModelState
import com.damianogiusti.slowdowner.domain.stems.StemSeparationRepository
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.TimeSource

/** The model a separation runs with: [files] are what [StemSeparatorDataSource] gets, in the order it expects. */
data class SeparationModel(val id: String, val files: List<File>)

/**
 * Runs one separation at a time in the application [scope], so the job outlives every screen. [dispatcher] must
 * run one task at a time (the separator is not meant to run twice at once) and is never the UI thread.
 */
class DefaultStemSeparationRepository(
    private val separator: StemSeparatorDataSource,
    private val library: LibraryStore,
    private val stems: StemsStore,
    private val models: StemModelRepository,
    private val model: SeparationModel,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher, // injected (IO, one thread); swap in tests
    private val logger: Logger,
) : StemSeparationRepository {

    // The job outlives its collectors and has no store to read back from, so the repository holds its
    // state, like the model repository holds its cache. Only a cold Flow leaves it.
    private val state = MutableStateFlow<SeparationJobState>(SeparationJobState.Idle)
    private val startLock = Mutex()

    @Volatile
    private var job: Job? = null

    override fun observe(): Flow<SeparationJobState> = state.asStateFlow()

    override suspend fun start(trackId: String): SeparationStart = startLock.withLock {
        if (state.value is SeparationJobState.Running) return@withLock SeparationStart.Busy
        val track = withContext(dispatcher) {
            if (models.observe().first() != StemModelState.Ready) return@withContext Precondition.ModelMissing
            findTrack(trackId)?.let { Precondition.Found(it.filePath) } ?: Precondition.TrackMissing
        }
        when (track) {
            Precondition.ModelMissing -> SeparationStart.ModelMissing
            Precondition.TrackMissing -> SeparationStart.TrackMissing
            is Precondition.Found -> {
                state.value = SeparationJobState.Running(trackId, 0f)
                logger.i("Stem separation started")
                val started = scope.launch(dispatcher) { run(trackId, track.inputPath) }
                // A job cancelled before its body ran (the scope is gone) never reaches the body's own handling.
                started.invokeOnCompletion { cause -> if (cause != null) markCancelled(trackId) }
                job = started
                SeparationStart.Started
            }
        }
    }

    override suspend fun cancel() {
        job?.cancelAndJoin()
    }

    override suspend fun acknowledge() {
        state.update { if (it is SeparationJobState.Finished) SeparationJobState.Idle else it }
    }

    /** For the quit path: cancels the job and waits for its cleanup, but not longer than [timeout]. */
    suspend fun shutdown(timeout: Duration) {
        if (withTimeoutOrNull(timeout) { cancel() } == null) logger.w("Stem separation did not stop in time")
    }

    private suspend fun findTrack(trackId: String) = try {
        library.find(trackId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.failure("Cannot read the track to separate", e)
        null
    }

    private suspend fun run(trackId: String, inputPath: String) {
        val begun = TimeSource.Monotonic.markNow()
        val result = try {
            separateAndSave(trackId, inputPath)
        } catch (e: CancellationException) {
            discardEmptyFolder(trackId)
            markCancelled(trackId)
            throw e
        } catch (e: Exception) {
            logger.e("Stem separation crashed", e)
            discardEmptyFolder(trackId)
            SeparationResult.Failed(SeparationFailureReason.UNEXPECTED)
        }
        state.value = SeparationJobState.Finished(trackId, result)
        when (result) {
            SeparationResult.Separated ->
                logger.i("Stem separation finished", mapOf("duration" to durationBucket(begun.elapsedNow().inWholeSeconds)))
            is SeparationResult.Failed -> logger.w("Stem separation failed", properties = mapOf("reason" to result.reason.name))
            SeparationResult.Cancelled -> Unit
        }
    }

    private suspend fun separateAndSave(trackId: String, inputPath: String): SeparationResult {
        val request = SeparationRequest(inputPath, stems.dirFor(trackId), model.files)
        var lastPercent = 0
        val outcome = separator.separate(request) { fraction ->
            // A segment takes seconds, but whole percents keep a short track from flooding the observers.
            val percent = (fraction * 100).toInt().coerceIn(0, 100)
            if (percent != lastPercent) {
                lastPercent = percent
                state.update { if (it is SeparationJobState.Running && it.trackId == trackId) it.copy(progress = percent / 100f) else it }
            }
        }
        return when (outcome) {
            is SeparationOutcome.Separated -> save(trackId, outcome)
            is SeparationOutcome.Failed -> {
                discardEmptyFolder(trackId)
                SeparationResult.Failed(outcome.reason.toDomain())
            }
        }
    }

    // Not cancellable: once the stems exist, the rows must follow, or the files are orphans.
    private suspend fun save(trackId: String, outcome: SeparationOutcome.Separated): SeparationResult =
        withContext(NonCancellable) {
            // Mode stays on the original file until stems can be played.
            val dto = TrackStemsDto(
                trackId = trackId,
                model = model.id,
                mode = TrackMode.FULL_TRACK.name,
                stems = outcome.stemFiles.map { (kind, fileName) -> StemFileDto(kind.name, fileName, gain = 1f) },
            )
            try {
                stems.add(dto)
                SeparationResult.Separated
            } catch (e: Exception) {
                discard(trackId)
                SeparationResult.Failed(
                    when {
                        // The track was removed while it was separated: its row is the foreign key.
                        isConstraintViolation(e) -> SeparationFailureReason.TRACK_REMOVED
                        e is IOException -> SeparationFailureReason.WRITE_ERROR.also { logger.failure("Cannot save the stems", e) }
                        else -> SeparationFailureReason.UNEXPECTED.also { logger.e("Cannot save the stems", e) }
                    },
                )
            }
        }

    /** The rows are gone or never came, so the files would be orphans. */
    private suspend fun discard(trackId: String) {
        try {
            stems.delete(trackId)
        } catch (e: Exception) {
            logger.failure("Cannot remove the stems of a failed separation", e)
        }
    }

    /** A first run that left nothing: the folder goes too. Not recursive, so a folder with stems stays. */
    private fun discardEmptyFolder(trackId: String) {
        try {
            stems.dirFor(trackId).delete()
        } catch (e: Exception) {
            logger.failure("Cannot remove an empty stem folder", e)
        }
    }

    /** Both the job's own handling and its completion handler call this: the second finds nothing to change. */
    private fun markCancelled(trackId: String) {
        var isChanged = false
        state.update {
            isChanged = it is SeparationJobState.Running && it.trackId == trackId
            if (isChanged) SeparationJobState.Finished(trackId, SeparationResult.Cancelled) else it
        }
        if (isChanged) logger.i("Stem separation cancelled")
    }

    private fun durationBucket(seconds: Long) = when {
        seconds < 60 -> "<1m"
        seconds < 180 -> "1-3m"
        seconds < 600 -> "3-10m"
        else -> ">10m"
    }

    private fun SeparationFailure.toDomain() = when (this) {
        SeparationFailure.DECODE_FAILED -> SeparationFailureReason.UNREADABLE_TRACK
        SeparationFailure.TOO_LONG -> SeparationFailureReason.TOO_LONG
        SeparationFailure.MODEL_FAILED -> SeparationFailureReason.MODEL_ERROR
        SeparationFailure.WRITE_FAILED -> SeparationFailureReason.WRITE_ERROR
    }

    private sealed interface Precondition {
        data object ModelMissing : Precondition
        data object TrackMissing : Precondition
        data class Found(val inputPath: String) : Precondition
    }
}
