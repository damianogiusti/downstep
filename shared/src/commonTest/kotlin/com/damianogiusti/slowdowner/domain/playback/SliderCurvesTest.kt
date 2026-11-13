package com.damianogiusti.slowdowner.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SliderCurvesTest {

    @Test
    fun `speed slider covers 25 to 200 percent with 100 in the dead zone`() {
        assertEquals(25f, SliderCurves.speedPercent(0), 0.01f)
        assertEquals(200f, SliderCurves.speedPercent(10_000), 0.01f)
        assertEquals(100f, SliderCurves.speedPercent(5_000))
        assertEquals(100f, SliderCurves.speedPercent(4_900))
        assertEquals(100f, SliderCurves.speedPercent(5_100))
    }

    @Test
    fun `half speed sits halfway down the slow half`() {
        // stretch 2 = 4^0.5: d - 100 = 2450.
        assertEquals(50f, SliderCurves.speedPercent(2_450), 0.01f)
        assertEquals(2_450, SliderCurves.speedPosition(50f))
    }

    @Test
    fun `speed position round-trips`() {
        for (percent in listOf(25f, 40f, 66.7f, 75f, 90f, 100f, 110f, 150f, 200f)) {
            val back = SliderCurves.speedPercent(SliderCurves.speedPosition(percent))
            assertEquals(percent, back, percent * 0.002f, "speed $percent")
        }
    }

    @Test
    fun `pitch slider is linear with a narrow dead zone`() {
        assertEquals(-12f, SliderCurves.pitchSemitones(0, snapToSemitones = false))
        assertEquals(12f, SliderCurves.pitchSemitones(10_000, snapToSemitones = false))
        assertEquals(0f, SliderCurves.pitchSemitones(4_990, snapToSemitones = false))
        assertEquals(-2f, SliderCurves.pitchSemitones(4_150, snapToSemitones = false), 0.001f)
        assertEquals(4_150, SliderCurves.pitchPosition(-2f))
    }

    @Test
    fun `snapping picks whole semitones and keeps the current cents`() {
        assertEquals(-2f, SliderCurves.pitchSemitones(4_100, snapToSemitones = true))
        assertEquals(3.25f, SliderCurves.pitchSemitones(SliderCurves.pitchPosition(3f), true, currentSemitones = 1.25f))
    }

    @Test
    fun `mix is neutral in the middle and reaches each channel at the ends`() {
        assertEquals(0.5f, SliderCurves.mixRight(5_000))
        assertEquals(0.5f, SliderCurves.mixRight(5_300))
        assertEquals(0f, SliderCurves.mixRight(0))
        assertEquals(1f, SliderCurves.mixRight(10_000))
        assertEquals(5_000, SliderCurves.mixPosition(0.5f))
        val quarter = SliderCurves.mixRight(SliderCurves.mixPosition(0.25f))
        assertTrue(kotlin.math.abs(quarter - 0.25f) < 0.001f, "mix $quarter")
    }

    @Test
    fun `fine pitch snaps to multiples of five cents`() {
        for (position in 0..10_000 step 37) {
            val value = SliderCurves.fineSemitones(position)
            val steps = value * 20
            assertEquals(steps.toInt().toFloat(), kotlin.math.round(steps), 0.001f, "position $position")
            assertTrue(value in -12f..12f, "position $position")
        }
    }

    @Test
    fun `fine pitch is exact to two decimals`() {
        for (position in 0..10_000 step 11) {
            val value = SliderCurves.fineSemitones(position)
            assertEquals((value * 100).let { kotlin.math.round(it) } / 100f, value, "position $position")
        }
    }

    @Test
    fun `fine pitch is zero in the dead zone and reaches both ends`() {
        assertEquals(0f, SliderCurves.fineSemitones(5_000))
        assertEquals(0f, SliderCurves.fineSemitones(4_990))
        assertEquals(0f, SliderCurves.fineSemitones(5_020))
        assertEquals(-12f, SliderCurves.fineSemitones(0))
        assertEquals(12f, SliderCurves.fineSemitones(10_000))
    }

    @Test
    fun `fine pitch position round-trips within a slider step`() {
        // The dead zone (4980..5020) all comes back as the centre.
        for (position in (0..10_000 step 53).filter { it < 4_980 || it > 5_020 }) {
            val back = SliderCurves.pitchPosition(SliderCurves.fineSemitones(position))
            // 5 cents are about 21 slider positions: snapping moves the value half of that at most.
            assertTrue(kotlin.math.abs(back - position) <= 11, "position $position came back as $back")
        }
    }
}
