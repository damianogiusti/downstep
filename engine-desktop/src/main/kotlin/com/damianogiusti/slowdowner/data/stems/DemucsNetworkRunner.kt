package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.stems.demucs.DemucsModel
import com.damianogiusti.slowdowner.data.stems.demucs.OverlapAdd
import com.damianogiusti.slowdowner.data.stems.demucs.SegmentCodec
import com.damianogiusti.slowdowner.data.stems.demucs.SegmentPlan
import com.damianogiusti.slowdowner.data.stems.demucs.TrackNormalisation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Path

/** The model's two files, [graph] (IR `.xml`) and [weights] (`.bin`). */
internal data class DemucsModelFiles(val graph: Path, val weights: Path)

/** The track to separate: 44.1 kHz [left] and [right] (the same array for mono) and its whole-track [normalisation]. */
internal class DemucsInput(val left: FloatArray, val right: FloatArray, val normalisation: TrackNormalisation)

/**
 * Runs Demucs over a whole track, segment by segment, and hands out finished samples as they become final.
 * Everything OpenVINO is created and closed inside [run], whatever happens, so the gigabytes it takes are
 * released when the call ends.
 *
 * Blocks are in the network's normalised domain (see [com.damianogiusti.slowdowner.data.stems.demucs.StemFinisher]
 * for the rest). Cancellation of the calling coroutine is checked before every segment.
 */
internal class DemucsNetworkRunner(private val openVinoDirectory: Path) {

    /**
     * [onBlock] gets `(start, count, channels)`: the stems' 12 planar channels (see [DemucsModel.channelIndex])
     * for the track samples `start until start + count`. The arrays are reused and may be changed in place.
     * [onBlock] may throw: the run stops and everything is closed.
     * @throws OpenVinoException if OpenVINO or the model fails.
     */
    suspend fun run(input: DemucsInput, files: DemucsModelFiles, onBlock: (start: Int, count: Int, channels: Array<FloatArray>) -> Unit) {
        val length = input.left.size
        val plan = SegmentPlan(length)
        val overlapAdd = OverlapAdd(plan, DemucsModel.STEMS.size * DemucsModel.CHANNELS)
        val codec = SegmentCodec()
        val segmentLength = DemucsModel.SEGMENT_LENGTH
        val segmentInput = FloatArray(DemucsModel.CHANNELS * segmentLength)
        val spectrogram = FloatArray(DemucsModel.SPECTROGRAM_FLOATS)
        val spectralOutput = FloatArray(DemucsModel.STEMS.size * DemucsModel.SPECTROGRAM_FLOATS)
        val timeOutput = FloatArray(DemucsModel.STEMS.size * DemucsModel.CHANNELS * segmentLength)
        val chunkOutput = Array(DemucsModel.STEMS.size * DemucsModel.CHANNELS) { FloatArray(segmentLength) }

        OpenVinoRuntime.load(openVinoDirectory).use { runtime ->
            runtime.loadModel(files.graph, files.weights).use { model ->
                model.bindInputs(listOf(SPECTRAL_SHAPE, TIME_SHAPE))
                // Inputs go in the order x, xt; a model with another layout fails here, not with garbage stems.
                if (model.outputSize(0) != spectralOutput.size.toLong() || model.outputSize(1) != timeOutput.size.toLong()) {
                    throw OpenVinoException("the outputs of the model are not those of htdemucs_6s")
                }
                for (segment in plan.segments) {
                    currentCoroutineContext().ensureActive()
                    plan.fillInput(segment, input.left, input.normalisation, segmentInput, 0)
                    plan.fillInput(segment, input.right, input.normalisation, segmentInput, segmentLength)
                    val statistics = codec.analyse(segmentInput, spectrogram)
                    model.setInput(0, spectrogram)
                    model.setInput(1, segmentInput)
                    model.infer()
                    model.copyOutput(0, spectralOutput)
                    model.copyOutput(1, timeOutput)
                    codec.synthesise(spectralOutput, timeOutput, statistics, chunkOutput)
                    overlapAdd.add(segment, chunkOutput, onBlock)
                }
            }
        }
    }

    private companion object {
        val SPECTRAL_SHAPE = longArrayOf(
            1, DemucsModel.SPECTROGRAM_CHANNELS.toLong(), DemucsModel.FREQUENCY_BINS.toLong(), DemucsModel.FRAMES.toLong(),
        )
        val TIME_SHAPE = longArrayOf(1, DemucsModel.CHANNELS.toLong(), DemucsModel.SEGMENT_LENGTH.toLong())
    }
}
