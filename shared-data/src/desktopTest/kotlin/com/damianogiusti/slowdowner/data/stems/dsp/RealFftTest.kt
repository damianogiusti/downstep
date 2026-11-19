package com.damianogiusti.slowdowner.data.stems.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class RealFftTest {

    @Test
    fun `when size is not a power of two then construction is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RealFft(12) }
        assertThrows(IllegalArgumentException::class.java) { RealFft(2) }
    }

    @Test
    fun `forward matches a naive DFT in double precision for several sizes`() {
        for (size in listOf(4, 8, 16, 64, 256, 1024, 4096)) {
            val input = randomSignal(size, seed = size)
            val fft = RealFft(size)
            val spectrum = FloatArray(2 * fft.binCount)

            fft.forward(input, 0, spectrum, 0)

            var maxError = 0.0
            var peak = 0.0
            for (k in 0 until fft.binCount) {
                var re = 0.0
                var im = 0.0
                for (n in 0 until size) {
                    val angle = 2.0 * PI * ((k.toLong() * n) % size) / size
                    re += input[n] * cos(angle)
                    im -= input[n] * sin(angle)
                }
                maxError = maxOf(maxError, abs(re - spectrum[2 * k]), abs(im - spectrum[2 * k + 1]))
                peak = maxOf(peak, abs(re), abs(im))
            }
            println("RealFft size=$size max abs error vs naive DFT = $maxError (peak $peak)")
            assertTrue("size $size error $maxError", maxError < 1e-5 * peak)
        }
    }

    @Test
    fun `forward and inverse round trip returns the input`() {
        for (size in listOf(4, 64, 1024, 4096)) {
            val input = randomSignal(size, seed = 7 * size)
            val fft = RealFft(size)
            val spectrum = FloatArray(2 * fft.binCount)
            val output = FloatArray(size)

            fft.forward(input, 0, spectrum, 0)
            fft.inverse(spectrum, 0, output, 0)

            val maxError = input.indices.maxOf { abs(input[it] - output[it]) }
            println("RealFft size=$size round trip max abs error = $maxError")
            assertTrue("size $size error $maxError", maxError < 1e-6)
        }
    }

    @Test
    fun `offsets are honoured on both sides`() {
        val fft = RealFft(16)
        val input = randomSignal(16, seed = 3)
        val padded = FloatArray(20).also { input.copyInto(it, 3) }
        val direct = FloatArray(2 * fft.binCount)
        val shifted = FloatArray(2 * fft.binCount + 5)

        fft.forward(input, 0, direct, 0)
        fft.forward(padded, 3, shifted, 5)

        for (i in direct.indices) assertEquals(direct[i], shifted[5 + i], 0f)
        val output = FloatArray(16 + 2)
        fft.inverse(shifted, 5, output, 2)
        for (i in input.indices) assertEquals(input[i], output[2 + i], 1e-6f)
    }

    @Test
    fun `a pure cosine puts its energy in one bin`() {
        val size = 256
        val fft = RealFft(size)
        val input = FloatArray(size) { cos(2.0 * PI * 5 * it / size).toFloat() }
        val spectrum = FloatArray(2 * fft.binCount)

        fft.forward(input, 0, spectrum, 0)

        assertEquals(size / 2f, spectrum[2 * 5], 1e-3f)
        assertEquals(0f, spectrum[2 * 5 + 1], 1e-3f)
        assertEquals(0f, spectrum[2 * 6], 1e-3f)
    }

    private fun randomSignal(size: Int, seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray(size) { random.nextFloat() * 2f - 1f }
    }
}
