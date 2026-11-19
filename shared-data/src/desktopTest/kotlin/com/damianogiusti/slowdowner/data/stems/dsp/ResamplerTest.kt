package com.damianogiusti.slowdowner.data.stems.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class ResamplerTest {

    private fun sine(frequency: Double, rate: Int, length: Int, amplitude: Double = 0.5): FloatArray =
        FloatArray(length) { (amplitude * sin(2.0 * PI * frequency * it / rate)).toFloat() }

    /** Largest difference to the analytic sine, ignoring [margin] samples at each end (the edges see zeros). */
    private fun maxSineError(output: FloatArray, frequency: Double, rate: Int, margin: Int, amplitude: Double = 0.5): Double {
        var worst = 0.0
        for (j in margin until output.size - margin) {
            val expected = amplitude * sin(2.0 * PI * frequency * j / rate)
            worst = maxOf(worst, abs(output[j] - expected))
        }
        return worst
    }

    @Test
    fun `when rates are equal then the input is copied`() {
        val input = sine(440.0, 44100, 1000)

        val output = Resampler(44100, 44100).resample(input)

        assertArrayEquals(input, output, 0f)
        assertTrue(output !== input)
    }

    @Test
    fun `output length keeps the duration`() {
        assertEquals(44100, Resampler(48000, 44100).outputLength(48000))
        assertEquals(88200, Resampler(22050, 44100).outputLength(44100))
        assertEquals(44100, Resampler(22050, 44100).outputLength(22050))
        assertEquals(0, Resampler(48000, 44100).outputLength(0))
        // 5 minutes at 48 kHz: the length is within half an output sample of the duration.
        assertEquals(13_230_000, Resampler(48000, 44100).outputLength(14_400_000))
    }

    @Test
    fun `when 48 kHz goes to 44 point 1 kHz then a sine keeps frequency phase and level`() {
        val input = sine(1000.0, 48000, 48000)

        val output = Resampler(48000, 44100).resample(input)

        assertEquals(44100, output.size)
        // No latency: output sample j is the sine at time j / 44100, not shifted.
        assertTrue(maxSineError(output, 1000.0, 44100, margin = 2000) < 2e-4)
    }

    @Test
    fun `when 22 kHz goes up to 44 point 1 kHz then a sine keeps frequency phase and level`() {
        val input = sine(3000.0, 22050, 22050)

        val output = Resampler(22050, 44100).resample(input)

        assertEquals(44100, output.size)
        assertTrue(maxSineError(output, 3000.0, 44100, margin = 2000) < 2e-4)
    }

    @Test
    fun `when rates share no factor then the kernels are computed per sample and the result is as good`() {
        // 44101 / 44100: 44100 phases, over the table limit.
        val input = sine(2000.0, 44101, 44101)

        val output = Resampler(44101, 44100).resample(input)

        assertEquals(44100, output.size)
        assertTrue(maxSineError(output, 2000.0, 44100, margin = 2000) < 2e-4)
    }

    @Test
    fun `when going down then a tone above the new Nyquist is removed`() {
        // 30 kHz at 96 kHz becomes 44.1 kHz: it is above 22.05 kHz and must not fold back (to 14.1 kHz).
        val input = sine(30000.0, 96000, 96000)

        val output = Resampler(96000, 44100).resample(input)

        var sumOfSquares = 0.0
        for (j in 2000 until output.size - 2000) sumOfSquares += output[j] * output[j]
        val rms = sqrt(sumOfSquares / (output.size - 4000))
        val inputRms = 0.5 / sqrt(2.0)
        assertTrue("alias at ${20 * kotlin.math.log10(rms / inputRms)} dB", rms / inputRms < 1e-3)
    }

    @Test
    fun `a constant stays constant away from the edges`() {
        val output = Resampler(48000, 44100).resample(FloatArray(48000) { 0.25f })

        for (j in 2000 until output.size - 2000) assertEquals(0.25f, output[j], 1e-6f)
    }

    @Test
    fun `a click keeps its place in time`() {
        val input = FloatArray(4800)
        input[2400] = 1f // 50 ms at 48 kHz

        val output = Resampler(48000, 44100).resample(input)

        val peak = output.indices.maxByOrNull { output[it] }
        assertEquals(2205, peak) // 50 ms at 44.1 kHz
    }

    @Test
    fun `an input shorter than the kernel still converts`() {
        val output = Resampler(48000, 44100).resample(floatArrayOf(1f, 1f, 1f))

        assertEquals(3, output.size)
        assertTrue(output.all { it.isFinite() })
    }
}
