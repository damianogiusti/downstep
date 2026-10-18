package com.damianogiusti.slowdowner.domain.tempo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TapTempoTest {

    private var now = 0L
    private val tapTempo = TapTempo { now }

    private fun tapEvery(millis: Long, times: Int): Float? {
        var bpm: Float? = null
        repeat(times) {
            bpm = tapTempo.tap()
            now += millis
        }
        return bpm
    }

    @Test
    fun `when tapped once then there is no tempo yet`() {
        assertNull(tapTempo.tap())
    }

    @Test
    fun `when tapped steadily then the tempo is the average interval`() {
        assertEquals(120f, tapEvery(500, 4))
        assertEquals(4, tapTempo.tapCount)
    }

    @Test
    fun `when the taps wobble then the tempo averages them, to a tenth of a BPM`() {
        // 600, 620, 580 ms: 1800 ms over three intervals is 100 BPM.
        for (gap in listOf(600L, 620L, 580L)) {
            tapTempo.tap()
            now += gap
        }
        assertEquals(100f, tapTempo.tap())
        now = 10_000
        repeat(2) {
            tapTempo.tap()
            now += 700
        }
        assertEquals(85.7f, tapTempo.bpm)
    }

    @Test
    fun `when more than eight taps come then only the last eight count`() {
        tapEvery(1000, 8) // 60 BPM
        assertEquals(120f, tapEvery(500, 8))
    }

    @Test
    fun `when the tapping pauses then it starts over`() {
        tapEvery(500, 4)
        now += TapTempo.RESET_MILLIS
        assertNull(tapTempo.tap())
        now += 400
        assertEquals(150f, tapTempo.tap())
    }

    @Test
    fun `when tapped faster than any tempo then it is held at the maximum`() {
        assertEquals(400f, tapEvery(10, 3))
    }

    @Test
    fun `when reset then the taps are forgotten`() {
        tapEvery(500, 3)
        tapTempo.reset()
        assertNull(tapTempo.bpm)
    }
}
