package com.damianogiusti.slowdowner.data.stems.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sample-rate conversion of one channel with a Kaiser-windowed sinc, on [FloatArray]s.
 *
 * The output sample `j` is the signal at time `j * inputRate / outputRate` (in input samples), so the
 * conversion adds no delay: a sample of the input at time `t` seconds is a sample of the output at the same
 * `t`. Samples before the start and after the end of the input count as zero. The output has
 * `round(n * outputRate / inputRate)` samples, so the duration of the audio is kept to half an output sample.
 *
 * Quality: the low-pass sits at 97 % of the lower of the two Nyquist frequencies, the window is a Kaiser
 * with beta 9 (about -90 dB stop band) and each side holds [ZERO_CROSSINGS] zero crossings of the sinc. Every
 * phase of the kernel is normalised to a gain of 1, so a constant stays exactly constant.
 *
 * Cost per output sample is about `2 * ZERO_CROSSINGS / cutoff` multiply-adds. When the reduced rate ratio has
 * at most [MAX_PHASES] phases (44.1 kHz from 48 kHz: 147) the kernels are computed once; otherwise each
 * output sample computes its own, which is slower and gives the same result.
 *
 * Plain Kotlin on purpose: the Android pipeline uses it too. Stateless after construction except for the
 * phase table, so one instance can serve several threads.
 */
class Resampler(val inputRate: Int, val outputRate: Int) {

    init {
        require(inputRate > 0 && outputRate > 0) { "rates must be positive, were $inputRate and $outputRate" }
    }

    private val divisor = gcd(inputRate, outputRate)
    private val inputStep = inputRate / divisor
    private val phaseCount = outputRate / divisor

    // Cutoff and width in input-sample units. Down-sampling narrows the cutoff and widens the kernel.
    private val cutoff = CUTOFF_FRACTION * min(1.0, outputRate.toDouble() / inputRate)
    private val halfWidth = ZERO_CROSSINGS / cutoff
    private val kernelSize = 2 * ceil(halfWidth).toInt() + 2

    /** True when the output is the input: the rates are equal. */
    val isIdentity: Boolean get() = inputRate == outputRate

    /** Samples of the output for [inputLength] samples of input. */
    fun outputLength(inputLength: Int): Int =
        ((inputLength.toLong() * outputRate + inputRate / 2) / inputRate).toInt()

    // phase p, the kernel for a fractional position p / phaseCount: kernelSize taps, the first one at
    // the input index floor(position) - kernelSize / 2 + 1.
    private val table: Array<FloatArray>? =
        if (phaseCount <= MAX_PHASES) Array(phaseCount) { kernel(it.toDouble() / phaseCount, FloatArray(kernelSize)) } else null

    /** Converts [input] and returns the new array. */
    fun resample(input: FloatArray): FloatArray {
        if (isIdentity) return input.copyOf()
        val output = FloatArray(outputLength(input.size))
        resample(input, output)
        return output
    }

    /** Converts [input] into [output], which must have exactly [outputLength] samples. */
    fun resample(input: FloatArray, output: FloatArray) {
        require(output.size == outputLength(input.size)) { "output has ${output.size} samples, expected ${outputLength(input.size)}" }
        if (isIdentity) {
            input.copyInto(output)
            return
        }
        val scratch = if (table == null) FloatArray(kernelSize) else null
        val first = kernelSize / 2 - 1 // index of the first tap relative to floor(position)
        var integerPart = 0L // floor(j * inputStep / phaseCount) without a Long division per sample
        var phase = 0
        for (j in output.indices) {
            val taps = table?.get(phase) ?: kernel(exactFraction(j), requireNotNull(scratch))
            val start = (if (table == null) floor(j.toDouble() * inputRate / outputRate).toLong() else integerPart).toInt() - first
            var sum = 0f
            if (start >= 0 && start + kernelSize <= input.size) {
                for (k in 0 until kernelSize) sum += taps[k] * input[start + k]
            } else {
                for (k in 0 until kernelSize) {
                    val index = start + k
                    if (index in input.indices) sum += taps[k] * input[index]
                }
            }
            output[j] = sum
            // Advance the position by inputStep / phaseCount.
            phase += inputStep
            integerPart += phase / phaseCount
            phase %= phaseCount
        }
    }

    // Fraction of the position of output sample j when there is no phase table (huge ratios), in Double.
    private fun exactFraction(j: Int): Double {
        val position = j.toDouble() * inputRate / outputRate
        return position - floor(position)
    }

    // Fills [taps] with the kernel for a position floor + [fraction], normalised to a gain of 1.
    private fun kernel(fraction: Double, taps: FloatArray): FloatArray {
        val first = kernelSize / 2 - 1
        var total = 0.0
        val values = DoubleArray(kernelSize)
        for (k in 0 until kernelSize) {
            // Distance in input samples from the tap to the position.
            val t = (k - first) - fraction
            val value = if (abs(t) >= halfWidth) 0.0 else cutoff * sinc(cutoff * t) * kaiser(t / halfWidth)
            values[k] = value
            total += value
        }
        for (k in 0 until kernelSize) taps[k] = (values[k] / total).toFloat()
        return taps
    }

    companion object {
        const val ZERO_CROSSINGS = 32
        private const val CUTOFF_FRACTION = 0.97
        private const val KAISER_BETA = 9.0
        private const val MAX_PHASES = 2048

        private val kaiserNorm = bessel0(KAISER_BETA)

        private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        // Kaiser window at x in -1..1.
        private fun kaiser(x: Double): Double = bessel0(KAISER_BETA * sqrt(max(0.0, 1.0 - x * x))) / kaiserNorm

        // Modified Bessel function of the first kind, order 0, by its power series.
        private fun bessel0(x: Double): Double {
            var term = 1.0
            var sum = 1.0
            val quarter = x * x / 4.0
            var k = 1
            while (term > 1e-14 * sum) {
                term *= quarter / (k.toDouble() * k)
                sum += term
                k++
            }
            return sum
        }

        private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}
