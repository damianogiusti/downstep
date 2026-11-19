package com.damianogiusti.slowdowner.data.stems.demucs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlanTest {

    private val identity = TrackNormalisation(MeanStd(0.0, 1.0))

    @Test
    fun `constants follow the model`() {
        assertEquals((44_100 * 7.8).toInt(), DemucsModel.SEGMENT_LENGTH)
        assertEquals(((1 - 0.25) * DemucsModel.SEGMENT_LENGTH).toInt(), DemucsModel.STRIDE)
        assertEquals(Math.ceil(DemucsModel.SEGMENT_LENGTH / DemucsModel.HOP.toDouble()).toInt(), DemucsModel.FRAMES)
    }

    @Test
    fun `an empty track has no segments`() {
        assertTrue(SegmentPlan(0).segments.isEmpty())
    }

    @Test
    fun `a short track is one short segment centred in the input`() {
        val plan = SegmentPlan(1000)

        val segment = plan.segments.single()
        assertEquals(0, segment.offset)
        assertEquals(1000, segment.length)
        assertEquals((343_980 - 1000) / 2, segment.trim)
        assertEquals(-segment.trim, segment.sourceStart)
    }

    @Test
    fun `a track of one stride is one full segment`() {
        // range(0, 257985, 257985) is [0]; the segment is cut at the end of the track.
        val segment = SegmentPlan(DemucsModel.STRIDE).segments.single()

        assertEquals(DemucsModel.STRIDE, segment.length)
        assertEquals((343_980 - 257_985) / 2, segment.trim)
    }

    @Test
    fun `a track of exactly one segment still gets a second segment for the tail`() {
        val segments = SegmentPlan(DemucsModel.SEGMENT_LENGTH).segments

        assertEquals(listOf(0, 257_985), segments.map { it.offset })
        assertEquals(listOf(343_980, 85_995), segments.map { it.length })
        assertEquals(0, segments[0].trim)
    }

    @Test
    fun `a long track has offsets at every stride and a short last segment`() {
        val segments = SegmentPlan(1_000_000).segments

        assertEquals(listOf(0, 257_985, 515_970, 773_955), segments.map { it.offset })
        assertEquals(listOf(343_980, 343_980, 343_980, 226_045), segments.map { it.length })
        assertEquals(listOf(0, 1, 2, 3), segments.map { it.index })
        // The last segment is centred: real neighbours on the left, zeros past the end on the right.
        assertEquals((343_980 - 226_045) / 2, segments.last().trim)
        assertEquals(773_955 - segments.last().trim, segments.last().sourceStart)
    }

    @Test
    fun `input of a short last segment has real samples on the left and zeros on the right`() {
        val plan = SegmentPlan(trackLength = 12, segmentLength = 10, stride = 7)
        val track = FloatArray(12) { (it + 1).toFloat() }
        val last = plan.segments.last()
        val input = FloatArray(10)

        plan.fillInput(last, track, identity, input, 0)

        // offset 7, length 5, trim 2: samples 5..14 of the track.
        assertEquals(5, last.length)
        assertArrayEquals(floatArrayOf(6f, 7f, 8f, 9f, 10f, 11f, 12f, 0f, 0f, 0f), input, 0f)
    }

    @Test
    fun `input of a track shorter than a segment is zero padded on both sides`() {
        val plan = SegmentPlan(trackLength = 4, segmentLength = 10, stride = 7)
        val input = FloatArray(10)

        plan.fillInput(plan.segments.single(), floatArrayOf(1f, 2f, 3f, 4f), identity, input, 0)

        // delta = 6, trim 3: three zeros, the track, three zeros.
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 2f, 3f, 4f, 0f, 0f, 0f), input, 0f)
    }

    @Test
    fun `padding is applied after the normalisation`() {
        val plan = SegmentPlan(trackLength = 2, segmentLength = 6, stride = 4)
        val input = FloatArray(6)

        plan.fillInput(plan.segments.single(), floatArrayOf(10f, 10f), TrackNormalisation(MeanStd(10.0, 2.0)), input, 0)

        assertTrue(input.all { it == 0f })
    }

    @Test
    fun `an overlap over half is rejected`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { SegmentPlan(100, segmentLength = 10, stride = 4) }
    }
}
