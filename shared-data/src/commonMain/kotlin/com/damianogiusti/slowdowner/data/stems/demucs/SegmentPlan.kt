package com.damianogiusti.slowdowner.data.stems.demucs

/**
 * How Demucs' `apply_model(split = True, shifts = 0)` cuts a track of [trackLength] samples: segments of
 * [segmentLength] every [stride] samples, `offsets = range(0, length, stride)`. A segment that runs past the
 * end of the track is short: the network still gets a full segment, padded as `TensorChunk.padded` does.
 */
class SegmentPlan(
    val trackLength: Int,
    val segmentLength: Int = DemucsModel.SEGMENT_LENGTH,
    val stride: Int = DemucsModel.STRIDE,
) {

    init {
        require(trackLength >= 0) { "trackLength must not be negative" }
        require(segmentLength > 0 && stride in 1..segmentLength) { "stride $stride must be in 1..$segmentLength" }
        // At most two segments may cover a sample: the streaming overlap-add depends on it.
        require(2 * stride >= segmentLength) { "overlap above 50 % is not supported" }
    }

    /**
     * One segment. [length] is the part inside the track (`min(trackLength - offset, segmentLength)`).
     * The network's input is the track from [sourceStart] for a whole segment, zeros where that falls outside
     * the track, so a short segment is centred in the input: [trim] samples of its output are dropped before it.
     */
    data class Segment(val index: Int, val offset: Int, val length: Int, val segmentLength: Int) {
        /** `(segmentLength - length) / 2`, the `delta // 2` of both `TensorChunk.padded` and `center_trim`. */
        val trim: Int get() = (segmentLength - length) / 2

        /** Track index of the first input sample; negative when the input starts with zeros. */
        val sourceStart: Int get() = offset - trim
    }

    val segments: List<Segment> = (0 until trackLength step stride).mapIndexed { index, offset ->
        Segment(index, offset, minOf(trackLength - offset, segmentLength), segmentLength)
    }

    /**
     * Fills [destination] (from [destinationOffset], [segmentLength] floats) with the input of [segment] taken
     * from [source], one channel of the track: each sample goes through [normalisation], samples outside the
     * track are zero (padding comes after the normalisation, as in Demucs).
     */
    fun fillInput(segment: Segment, source: FloatArray, normalisation: TrackNormalisation, destination: FloatArray, destinationOffset: Int) {
        require(source.size >= trackLength) { "source has ${source.size} samples, the track has $trackLength" }
        val start = segment.sourceStart
        for (k in 0 until segmentLength) {
            val index = start + k
            destination[destinationOffset + k] = if (index in 0 until trackLength) normalisation.normalise(source[index]) else 0f
        }
    }
}
