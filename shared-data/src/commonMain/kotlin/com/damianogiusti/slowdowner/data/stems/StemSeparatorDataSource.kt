package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import java.io.File

/**
 * Splits one track into stem files with a model. One implementation per platform and model (Demucs on the
 * desktop). It does the heavy work on the calling thread and uses no dispatcher: the repository that calls it
 * chooses one, and runs one separation at a time.
 */
interface StemSeparatorDataSource {

    /**
     * Separates the track at [SeparationRequest.inputPath] and writes one MP3 per stem into
     * [SeparationRequest.outputDir], named `<stem>.mp3` with the stem in lower case. Nothing else is left there:
     * on a failure or a cancellation the files of this run are removed and files of an earlier separation stay.
     *
     * [onProgress] gets the fraction done (0..1) from the calling thread, about once per segment.
     * Cancelling the calling coroutine stops the work between segments and throws the usual
     * `CancellationException`, after the resources have been released.
     */
    suspend fun separate(request: SeparationRequest, onProgress: (Float) -> Unit): SeparationOutcome
}

/**
 * [modelFiles] are the files the model needs, as the model repository stores them; the implementation picks
 * what it uses. [outputDir] exists already (the stems store creates it).
 */
data class SeparationRequest(val inputPath: String, val outputDir: File, val modelFiles: List<File>)

sealed interface SeparationOutcome {
    /** [stemFiles] maps each stem to its file name inside the output folder. */
    data class Separated(val stemFiles: Map<StemKind, String>) : SeparationOutcome

    data class Failed(val reason: SeparationFailure) : SeparationOutcome
}

enum class SeparationFailure {
    /** The track cannot be read, or has no audio, or has more than two channels. */
    DECODE_FAILED,

    /** The decoded track does not fit in the memory the separation may use. */
    TOO_LONG,

    /** The model is missing or damaged, or the runtime failed while loading or running it. */
    MODEL_FAILED,

    /** A stem file cannot be created or filled (disk full, folder gone). */
    WRITE_FAILED,
}
