package com.damianogiusti.slowdowner.data.stems.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * FFT of real signals of a power-of-two [size] (at least 4), on [FloatArray]s, with no allocation after construction.
 *
 * Layout of a spectrum: `size / 2 + 1` bins, **interleaved** (`re0, im0, re1, im1, ...`), so `2 * binCount`
 * floats. One array holds a whole spectrogram frame after frame with no objects per bin, and a bin is one
 * cache line neighbour of the next.
 *
 * Method: the real signal is packed as `size / 2` complex values (even samples real, odd samples imaginary),
 * transformed with an iterative radix-2 FFT, and unpacked to the half spectrum. Twiddles are computed in
 * `Double` and stored as `Float`, so the table is exact to Float precision; the butterflies are Float.
 *
 * Not thread-safe: the scratch arrays belong to the instance. Use one instance per thread.
 */
class RealFft(val size: Int) {

    init {
        require(size >= 4 && size and (size - 1) == 0) { "size must be a power of two >= 4, was $size" }
    }

    /** Number of complex bins of a spectrum: `size / 2 + 1`. */
    val binCount: Int = size / 2 + 1

    private val half = size / 2
    private val re = FloatArray(half)
    private val im = FloatArray(half)
    private val bitReversed = IntArray(half)

    // Twiddles of the half-size complex FFT: exp(-2 pi i j / half), j < half / 2.
    private val fftCos = FloatArray(maxOf(half / 2, 1))
    private val fftSin = FloatArray(maxOf(half / 2, 1))

    // Unpack twiddles exp(-2 pi i k / size), k in 0..half, stored as cos and sin of 2 pi k / size.
    private val packCos = FloatArray(half + 1)
    private val packSin = FloatArray(half + 1)

    init {
        val bits = Integer.numberOfTrailingZeros(half)
        for (i in 0 until half) {
            bitReversed[i] = if (bits == 0) 0 else Integer.reverse(i) ushr (32 - bits)
        }
        for (j in fftCos.indices) {
            val angle = 2.0 * PI * j / half
            fftCos[j] = cos(angle).toFloat()
            fftSin[j] = sin(angle).toFloat()
        }
        for (k in 0..half) {
            val angle = 2.0 * PI * k / size
            packCos[k] = cos(angle).toFloat()
            packSin[k] = sin(angle).toFloat()
        }
    }

    /**
     * Transforms [size] samples read from [input] at [inputOffset]. Writes `2 * binCount` floats
     * (interleaved re/im) into [output] at [outputOffset]. The bins are not scaled.
     */
    fun forward(input: FloatArray, inputOffset: Int, output: FloatArray, outputOffset: Int) {
        for (i in 0 until half) {
            val target = bitReversed[i]
            re[target] = input[inputOffset + 2 * i]
            im[target] = input[inputOffset + 2 * i + 1]
        }
        butterflies(re, im)
        for (k in 0..half) {
            val j = if (k == half) 0 else k
            val m = if (k == 0) 0 else half - k
            val ar = re[j]
            val ai = im[j]
            val br = re[m]
            val bi = -im[m]
            val er = 0.5f * (ar + br)
            val ei = 0.5f * (ai + bi)
            val dr = 0.5f * (ar - br)
            val di = 0.5f * (ai - bi)
            // odd part = d / i = (di, -dr); times exp(-i angle) = (c, -s)
            val or = di
            val oi = -dr
            val c = packCos[k]
            val s = packSin[k]
            output[outputOffset + 2 * k] = er + or * c + oi * s
            output[outputOffset + 2 * k + 1] = ei + oi * c - or * s
        }
    }

    /**
     * Inverse of [forward]: reads `2 * binCount` floats from [input] at [inputOffset] and writes [size] samples
     * to [output] at [outputOffset]. The result is already divided by [size], so `inverse(forward(x)) == x`.
     * The imaginary parts of bin 0 and of the last bin are ignored, as for any real signal.
     */
    fun inverse(input: FloatArray, inputOffset: Int, output: FloatArray, outputOffset: Int) {
        for (k in 0 until half) {
            val m = half - k
            val xr = input[inputOffset + 2 * k]
            val xi = input[inputOffset + 2 * k + 1]
            val yr = input[inputOffset + 2 * m]
            val yi = -input[inputOffset + 2 * m + 1]
            val er = 0.5f * (xr + yr)
            val ei = 0.5f * (xi + yi)
            val dr = 0.5f * (xr - yr)
            val di = 0.5f * (xi - yi)
            // odd part = d * exp(+i angle) = d * (c, s)
            val c = packCos[k]
            val s = packSin[k]
            val or = dr * c - di * s
            val oi = dr * s + di * c
            // z = even + i * odd, stored swapped (im, re) so the forward butterflies compute the inverse.
            val target = bitReversed[k]
            re[target] = ei + or
            im[target] = er - oi
        }
        butterflies(re, im)
        val scale = 1f / half
        for (i in 0 until half) {
            // Undo the swap: inverse(z) = swap(forward(swap(z))) / half.
            output[outputOffset + 2 * i] = im[i] * scale
            output[outputOffset + 2 * i + 1] = re[i] * scale
        }
    }

    // Input is already in bit-reversed order.
    private fun butterflies(real: FloatArray, imag: FloatArray) {
        var i = 0
        while (i < half) {
            val ur = real[i]
            val ui = imag[i]
            val vr = real[i + 1]
            val vi = imag[i + 1]
            real[i] = ur + vr
            imag[i] = ui + vi
            real[i + 1] = ur - vr
            imag[i + 1] = ui - vi
            i += 2
        }
        var length = 4
        while (length <= half) {
            val halfLength = length / 2
            val step = half / length
            var block = 0
            while (block < half) {
                var twiddle = 0
                for (j in block until block + halfLength) {
                    val c = fftCos[twiddle]
                    val s = fftSin[twiddle]
                    val k = j + halfLength
                    val tr = real[k] * c + imag[k] * s
                    val ti = imag[k] * c - real[k] * s
                    real[k] = real[j] - tr
                    imag[k] = imag[j] - ti
                    real[j] += tr
                    imag[j] += ti
                    twiddle += step
                }
                block += length
            }
            length *= 2
        }
    }
}
