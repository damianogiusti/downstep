package com.damianogiusti.slowdowner.data.stems.demucs

/**
 * Demucs' triangular cross-fade weights for a segment: `cat(arange(1, n / 2 + 1), arange(n - n / 2, 0, -1))`
 * divided by their maximum (transition power 1).
 */
fun triangularWeights(segmentLength: Int): FloatArray {
    require(segmentLength > 0) { "segmentLength must be positive" }
    val half = segmentLength / 2
    val peak = maxOf(half, segmentLength - half).toFloat()
    return FloatArray(segmentLength) { i ->
        val rising = i < half
        (if (rising) i + 1 else segmentLength - i).toFloat() / peak
    }
}

/**
 * The `out += weight * chunk_out; sum_weight += weight; out /= sum_weight` of `apply_model`, streamed. With an
 * overlap of at most 50 % a sample belongs to at most two segments, so once a segment is added the samples
 * before the next segment's offset are final: they leave through the callback and only the overlap stays.
 * Memory is `channelCount + 1` arrays of one segment, however long the track.
 *
 * Results equal the whole-array computation exactly (same operations in the same order per sample).
 * Segments must be added in order. Not thread-safe.
 */
class OverlapAdd(private val plan: SegmentPlan, private val channelCount: Int) {

    private val segmentLength = plan.segmentLength
    private val weights = triangularWeights(segmentLength)
    private val accumulated = Array(channelCount) { FloatArray(segmentLength) }
    private val weightSum = FloatArray(segmentLength)
    private var nextIndex = 0

    /**
     * Adds [output], [channelCount] planar channels of the network's output for [segment] (each
     * `plan.segmentLength` long, the whole padded segment), then reports the samples that became final:
     * `onFinished(start, count, channels)` with the channels' first [count] samples; the arrays are reused,
     * so read them before returning.
     *
     * A short segment contributes `weights[0 until length]`, not a re-centred triangle, as in Demucs.
     */
    fun add(segment: SegmentPlan.Segment, output: Array<FloatArray>, onFinished: (start: Int, count: Int, channels: Array<FloatArray>) -> Unit) {
        require(segment.index == nextIndex) { "expected segment $nextIndex, got ${segment.index}" }
        require(output.size == channelCount) { "expected $channelCount channels, got ${output.size}" }
        nextIndex++
        val length = segment.length
        val trim = segment.trim
        for (channel in 0 until channelCount) {
            val target = accumulated[channel]
            val source = output[channel]
            for (j in 0 until length) target[j] += weights[j] * source[trim + j]
        }
        for (j in 0 until length) weightSum[j] += weights[j]

        val isLast = segment.index == plan.segments.lastIndex
        val count = if (isLast) length else plan.stride
        for (channel in 0 until channelCount) {
            val target = accumulated[channel]
            for (j in 0 until count) target[j] /= weightSum[j]
        }
        onFinished(segment.offset, count, accumulated)

        if (!isLast) {
            val kept = segmentLength - plan.stride
            for (channel in 0 until channelCount) {
                val target = accumulated[channel]
                target.copyInto(target, 0, plan.stride, segmentLength)
                target.fill(0f, kept, segmentLength)
            }
            weightSum.copyInto(weightSum, 0, plan.stride, segmentLength)
            weightSum.fill(0f, kept, segmentLength)
        }
    }
}
