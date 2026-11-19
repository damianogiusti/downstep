package com.damianogiusti.slowdowner.data.stems.demucs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class OverlapAddTest {

    @Test
    fun `weights of an even and an odd segment are triangles with a peak of one`() {
        assertArrayEquals(floatArrayOf(1f / 3, 2f / 3, 1f, 1f, 2f / 3, 1f / 3), triangularWeights(6), 0f)
        assertArrayEquals(floatArrayOf(1f / 3, 2f / 3, 1f, 2f / 3, 1f / 3), triangularWeights(5), 0f)
    }

    @Test
    fun `weights of the model segment are symmetric and peak at one`() {
        val weights = triangularWeights(DemucsModel.SEGMENT_LENGTH)

        assertEquals(DemucsModel.SEGMENT_LENGTH, weights.size)
        assertEquals(1f, weights.max(), 0f)
        assertEquals(1f / 171_990, weights.first(), 0f)
        for (i in 0 until 1000) assertEquals(weights[i], weights[weights.size - 1 - i], 0f)
    }

    // A direct port of the split branch of apply_model, on whole arrays.
    private fun wholeArray(plan: SegmentPlan, outputs: List<Array<FloatArray>>, channels: Int): Array<FloatArray> {
        val weights = triangularWeights(plan.segmentLength)
        val out = Array(channels) { FloatArray(plan.trackLength) }
        val sum = FloatArray(plan.trackLength)
        for (segment in plan.segments) {
            for (j in 0 until segment.length) {
                for (c in 0 until channels) out[c][segment.offset + j] += weights[j] * outputs[segment.index][c][segment.trim + j]
                sum[segment.offset + j] += weights[j]
            }
        }
        for (c in 0 until channels) for (i in out[c].indices) out[c][i] /= sum[i]
        return out
    }

    private fun streamed(plan: SegmentPlan, outputs: List<Array<FloatArray>>, channels: Int): Pair<Array<FloatArray>, List<Pair<Int, Int>>> {
        val result = Array(channels) { FloatArray(plan.trackLength) }
        val blocks = mutableListOf<Pair<Int, Int>>()
        val overlapAdd = OverlapAdd(plan, channels)
        for (segment in plan.segments) {
            overlapAdd.add(segment, outputs[segment.index]) { start, count, block ->
                blocks += start to count
                for (c in 0 until channels) block[c].copyInto(result[c], start, 0, count)
            }
        }
        return result to blocks
    }

    private fun check(trackLength: Int, segmentLength: Int, stride: Int) {
        val plan = SegmentPlan(trackLength, segmentLength, stride)
        val random = Random(trackLength)
        val outputs = plan.segments.map { Array(3) { FloatArray(segmentLength) { random.nextFloat() * 2 - 1 } } }

        val expected = wholeArray(plan, outputs, 3)
        val (actual, blocks) = streamed(plan, outputs, 3)

        for (c in 0 until 3) assertArrayEquals("channel $c", expected[c], actual[c], 0f)
        // The blocks tile the track in order, with no gap and no overlap.
        var position = 0
        for ((start, count) in blocks) {
            assertEquals(position, start)
            position += count
        }
        assertEquals(trackLength, position)
    }

    @Test
    fun `streaming equals the whole-array computation on a long track`() = check(trackLength = 2000, segmentLength = 100, stride = 75)

    @Test
    fun `streaming equals the whole-array computation when the last segment is short`() = check(trackLength = 350, segmentLength = 100, stride = 75)

    @Test
    fun `streaming equals the whole-array computation for one short segment`() = check(trackLength = 40, segmentLength = 100, stride = 75)

    @Test
    fun `streaming equals the whole-array computation with the lengths of the model`() =
        check(trackLength = 700_000, segmentLength = DemucsModel.SEGMENT_LENGTH, stride = DemucsModel.STRIDE)

    @Test
    fun `streaming equals the whole-array computation with a tail of one sample`() = check(trackLength = 301, segmentLength = 100, stride = 75)

    @Test
    fun `segments out of order are refused`() {
        val plan = SegmentPlan(300, 100, 75)
        val overlapAdd = OverlapAdd(plan, 1)

        assertThrows(IllegalArgumentException::class.java) {
            overlapAdd.add(plan.segments[1], arrayOf(FloatArray(100))) { _, _, _ -> }
        }
    }
}
