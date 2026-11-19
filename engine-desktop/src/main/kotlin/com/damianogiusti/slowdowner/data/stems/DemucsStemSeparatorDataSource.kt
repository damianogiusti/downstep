package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.playback.NativePcm
import com.damianogiusti.slowdowner.data.playback.NativeStemWriter
import com.damianogiusti.slowdowner.data.playback.PcmDecodeResult
import com.damianogiusti.slowdowner.data.playback.StemFormat
import com.damianogiusti.slowdowner.data.playback.StemWriterOpenResult
import com.damianogiusti.slowdowner.data.stems.demucs.DemucsModel
import com.damianogiusti.slowdowner.data.stems.demucs.StemFinisher
import com.damianogiusti.slowdowner.data.stems.demucs.TrackNormalisation
import com.damianogiusti.slowdowner.data.stems.demucs.monoMeanAndStd
import com.damianogiusti.slowdowner.data.stems.dsp.Resampler
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Demucs `htdemucs_6s` on OpenVINO, in this process. Decodes the track, runs the network segment by segment
 * and streams every finished block of samples to six MP3 writers, so memory holds the decoded track, one
 * segment's buffers and one overlap window, never six whole-track float arrays.
 *
 * One separation at a time (a lock), because OpenVINO already uses every core and a second model would add
 * gigabytes. The OpenVINO runtime and model live for one call and are closed in `finally`. The work runs on
 * the calling thread; cancellation is checked between segments.
 *
 * Files are written as `<stem>.mp3.part` and renamed when all six are complete, so a failed run leaves the
 * files of an earlier separation as they were.
 *
 * [openVinoDirectory] is the folder of the OpenVINO libraries; by default the system property of
 * [OpenVinoRuntime.LIBRARY_DIR_PROPERTY].
 */
class DemucsStemSeparatorDataSource(
    private val logger: Logger,
    private val openVinoDirectory: Path? = null,
) : StemSeparatorDataSource {

    private val oneAtATime = Mutex()

    override suspend fun separate(request: SeparationRequest, onProgress: (Float) -> Unit): SeparationOutcome =
        oneAtATime.withLock {
            val run = Run(request, onProgress)
            try {
                run.execute()
            } finally {
                run.cleanUp()
            }
        }

    /** One call: owns the resources that [cleanUp] releases, whatever happens. */
    private inner class Run(private val request: SeparationRequest, private val onProgress: (Float) -> Unit) {
        private val writers = mutableMapOf<StemKind, NativeStemWriter>()
        private val partFiles = mutableMapOf<StemKind, File>()
        private var isSuccess = false

        suspend fun execute(): SeparationOutcome {
            val modelFiles = findModelFiles() ?: return failed(SeparationFailure.MODEL_FAILED, "model files missing")
            val track = when (val decoded = decode()) {
                is Decoded.Track -> decoded
                is Decoded.Failure -> return failed(decoded.reason, "decode")
            }
            currentCoroutineContext().ensureActive()
            if (!openWriters()) return failed(SeparationFailure.WRITE_FAILED, "open writers")
            try {
                runNetwork(modelFiles, track)
            } catch (e: OpenVinoException) {
                return failed(SeparationFailure.MODEL_FAILED, "model", e)
            } catch (e: WriteFailedException) {
                return failed(SeparationFailure.WRITE_FAILED, "write", e)
            }
            return finishFiles()
        }

        fun cleanUp() {
            // The writers hold the .part files open. The model and OpenVINO are closed by the network runner.
            writers.values.forEach { it.close() }
            if (!isSuccess) partFiles.values.forEach { Files.deleteIfExists(it.toPath()) }
        }

        private fun failed(reason: SeparationFailure, stage: String, error: Throwable? = null): SeparationOutcome {
            val properties = mapOf("reason" to reason.name, "stage" to stage)
            if (error is IOException || error is OpenVinoException || error == null) {
                logger.w("Stem separation failed", error, properties)
            } else {
                logger.e("Stem separation failed", error, properties)
            }
            return SeparationOutcome.Failed(reason)
        }

        private fun findModelFiles(): DemucsModelFiles? {
            val graph = request.modelFiles.firstOrNull { it.extension == "xml" && it.isFile }
            val weights = request.modelFiles.firstOrNull { it.extension == "bin" && it.isFile }
            return if (graph != null && weights != null) DemucsModelFiles(graph.toPath(), weights.toPath()) else null
        }

        private fun decode(): Decoded {
            val pcm = when (val result = NativePcm.decode(request.inputPath, DECODE_BUDGET_BYTES)) {
                is PcmDecodeResult.Decoded -> result.pcm
                PcmDecodeResult.Failed -> return Decoded.Failure(SeparationFailure.DECODE_FAILED)
                PcmDecodeResult.TooLong -> return Decoded.Failure(SeparationFailure.TOO_LONG)
            }
            pcm.use {
                val rate = it.sampleRate
                val frames = it.frames
                if (rate <= 0 || frames < MIN_FRAMES) return Decoded.Failure(SeparationFailure.DECODE_FAILED)
                if (frames > MAX_SECONDS.toLong() * rate) return Decoded.Failure(SeparationFailure.TOO_LONG)
                return try {
                    val channels = (0 until it.channels.coerceIn(1, 2)).map { channel -> readChannel(it, channel, frames.toInt()) }
                    val resampler = Resampler(rate, DemucsModel.SAMPLE_RATE)
                    val left = resampler.resample(channels[0])
                    val right = if (channels.size > 1) resampler.resample(channels[1]) else left
                    Decoded.Track(left, right)
                } catch (_: OutOfMemoryError) {
                    Decoded.Failure(SeparationFailure.TOO_LONG)
                }
            }
        }

        private fun readChannel(pcm: NativePcm, channel: Int, frames: Int): FloatArray {
            val samples = FloatArray(frames)
            val chunk = FloatArray(READ_CHUNK_FRAMES)
            var position = 0
            while (position < frames) {
                val copied = pcm.copy(channel, position.toLong(), chunk).coerceAtMost(frames - position)
                if (copied <= 0) break
                chunk.copyInto(samples, position, 0, copied)
                position += copied
            }
            return samples
        }

        private fun openWriters(): Boolean {
            for (kind in DemucsModel.STEMS) {
                val part = File(request.outputDir, "${fileNameOf(kind)}$PART_SUFFIX")
                partFiles[kind] = part
                val format = StemFormat(DemucsModel.SAMPLE_RATE, DemucsModel.CHANNELS)
                when (val opened = NativeStemWriter.open(part.path, format)) {
                    is StemWriterOpenResult.Opened -> writers[kind] = opened.writer
                    else -> return false
                }
            }
            return true
        }

        private suspend fun runNetwork(files: DemucsModelFiles, track: Decoded.Track) {
            val length = track.left.size
            val normalisation = TrackNormalisation(monoMeanAndStd(track.left, track.right))
            val finisher = StemFinisher(arrayOf(track.left, track.right), normalisation)
            val directory = openVinoDirectory ?: defaultOpenVinoDirectory()
            DemucsNetworkRunner(directory).run(DemucsInput(track.left, track.right, normalisation), files) { start, count, block ->
                finisher.finish(block, start, count)
                writeBlock(block, count)
                onProgress(((start + count).toFloat() / length).coerceIn(0f, 1f))
            }
        }

        private fun writeBlock(block: Array<FloatArray>, count: Int) {
            val interleaved = FloatArray(count * DemucsModel.CHANNELS)
            for ((index, kind) in DemucsModel.STEMS.withIndex()) {
                val left = block[DemucsModel.channelIndex(index, 0)]
                val right = block[DemucsModel.channelIndex(index, 1)]
                for (i in 0 until count) {
                    interleaved[2 * i] = left[i]
                    interleaved[2 * i + 1] = right[i]
                }
                if (!requireNotNull(writers[kind]).write(interleaved)) throw WriteFailedException()
            }
        }

        private fun finishFiles(): SeparationOutcome {
            writers.values.forEach { it.close() }
            if (writers.values.any { !it.isComplete }) return failed(SeparationFailure.WRITE_FAILED, "finish writers")
            return try {
                val names = DemucsModel.STEMS.associateWith { fileNameOf(it) }
                for ((kind, name) in names) {
                    Files.move(requireNotNull(partFiles[kind]).toPath(), File(request.outputDir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                isSuccess = true
                SeparationOutcome.Separated(names)
            } catch (e: IOException) {
                failed(SeparationFailure.WRITE_FAILED, "rename", e)
            }
        }
    }

    private fun defaultOpenVinoDirectory(): Path =
        Path.of(requireNotNull(System.getProperty(OpenVinoRuntime.LIBRARY_DIR_PROPERTY)) {
            "set -D${OpenVinoRuntime.LIBRARY_DIR_PROPERTY}=<folder of the OpenVINO libraries>"
        })

    private sealed interface Decoded {
        class Track(val left: FloatArray, val right: FloatArray) : Decoded
        class Failure(val reason: SeparationFailure) : Decoded
    }

    private class WriteFailedException : IOException("a stem writer failed")

    private companion object {
        const val PART_SUFFIX = ".part"

        /** Longest track accepted: its two channels at 44.1 kHz are about 420 MB of float arrays at this length. */
        const val MAX_SECONDS = 20 * 60
        const val MIN_FRAMES = 2L
        const val READ_CHUNK_FRAMES = 1 shl 20

        /** Native memory the decoder may use: 20 minutes of 96 kHz stereo floats and a margin. */
        const val DECODE_BUDGET_BYTES = 1L shl 30

        fun fileNameOf(kind: StemKind): String = "${kind.name.lowercase()}.mp3"
    }
}
