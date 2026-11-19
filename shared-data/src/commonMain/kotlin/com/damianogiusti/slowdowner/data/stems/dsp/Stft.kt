package com.damianogiusti.slowdowner.data.stems.dsp

import kotlin.math.PI
import kotlin.math.cos

/** Periodic Hann window, `0.5 - 0.5 cos(2 pi n / size)` for n in `0 until size` (not the symmetric one). */
fun periodicHannWindow(size: Int): FloatArray {
    require(size > 0) { "size must be positive, was $size" }
    return FloatArray(size) { n -> (0.5 - 0.5 * cos(2.0 * PI * n / size)).toFloat() }
}

/**
 * Short-time Fourier transform of an already padded signal. Padding and scaling are the caller's job
 * ([StftPadding], [scaleInPlace]) because the pipelines differ:
 *
 * - Spleeter: `StftPadding.leadingZeros(signal, fftSize - hop)`, no scaling.
 * - Demucs / `torch.stft(center = true, pad_mode = "reflect", normalized = true)`:
 *   `StftPadding.reflect(signal, fftSize / 2)`, spectra scaled by `1 / sqrt(fftSize)`; for `torch.istft`
 *   scale the spectra back by `sqrt(fftSize)` before [inverse] and drop `fftSize / 2` samples on each side.
 *
 * Spectra layout: frame after frame, each frame `2 * binCount` floats interleaved (re, im), see [RealFft].
 * Frame `k` starts at sample `k * hop`. [fftSize] must be a multiple of [hop].
 *
 * Not thread-safe (scratch buffers). Use one instance per thread.
 */
class Stft(val fftSize: Int, val hop: Int, window: FloatArray) {

    init {
        require(window.size == fftSize) { "window has ${window.size} samples, fftSize is $fftSize" }
        require(hop > 0 && fftSize % hop == 0) { "fftSize $fftSize must be a multiple of hop $hop" }
    }

    private val window = window.copyOf()
    private val squaredWindow = FloatArray(fftSize) { this.window[it] * this.window[it] }
    private val fft = RealFft(fftSize)
    private val frame = FloatArray(fftSize)
    private val overlaps = fftSize / hop

    /** Floats per frame of a spectrogram: `2 * (fftSize / 2 + 1)`. */
    val frameStride: Int = 2 * fft.binCount

    /** Number of whole frames in a signal of [signalLength] samples; samples after the last whole frame are ignored. */
    fun frameCount(signalLength: Int): Int =
        if (signalLength < fftSize) 0 else (signalLength - fftSize) / hop + 1

    /** Length of the signal rebuilt by [inverse] from [frameCount] frames. */
    fun signalLength(frameCount: Int): Int = if (frameCount == 0) 0 else (frameCount - 1) * hop + fftSize

    /**
     * Writes `frameCount(signalLength) * frameStride` floats into [spectra] (which may be larger).
     * Returns the number of frames.
     */
    fun forward(signal: FloatArray, signalLength: Int, spectra: FloatArray): Int {
        val frames = frameCount(signalLength)
        require(spectra.size >= frames * frameStride) { "spectra too small for $frames frames" }
        for (k in 0 until frames) {
            val start = k * hop
            for (n in 0 until fftSize) frame[n] = signal[start + n] * window[n]
            fft.forward(frame, 0, spectra, k * frameStride)
        }
        return frames
    }

    /** Convenience over [forward] that allocates the spectra. */
    fun forward(signal: FloatArray): FloatArray {
        val spectra = FloatArray(frameCount(signal.size) * frameStride)
        forward(signal, signal.size, spectra)
        return spectra
    }

    /**
     * Weighted overlap-add of [frameCount] frames of [spectra] into [output] (at least
     * `signalLength(frameCount)` long; that many samples are written), normalised by the sum of the squared
     * windows. Where that sum is below 1e-8 (the very first samples with a Hann window) the output is zero.
     */
    fun inverse(spectra: FloatArray, frameCount: Int, output: FloatArray) {
        val length = signalLength(frameCount)
        require(output.size >= length) { "output too small: ${output.size} < $length" }
        require(spectra.size >= frameCount * frameStride) { "spectra too small for $frameCount frames" }
        output.fill(0f, 0, length)
        for (k in 0 until frameCount) {
            fft.inverse(spectra, k * frameStride, frame, 0)
            val start = k * hop
            for (n in 0 until fftSize) output[start + n] += frame[n] * window[n]
        }
        // Hop-sized blocks: block q holds the samples of frames q, q-1, ... q-overlaps+1 at window offsets
        // r, r + hop, ... so the squared-window sum is a short loop with no per-sample division.
        val blocks = frameCount - 1 + overlaps
        for (q in 0 until blocks) {
            val first = maxOf(0, q - (frameCount - 1))
            val last = minOf(q, overlaps - 1)
            val base = q * hop
            for (r in 0 until hop) {
                var envelope = 0f
                for (j in first..last) envelope += squaredWindow[r + j * hop]
                output[base + r] = if (envelope < MIN_ENVELOPE) 0f else output[base + r] / envelope
            }
        }
    }

    private companion object {
        const val MIN_ENVELOPE = 1e-8f
    }
}

/** Padding policies for [Stft]. Each returns a new array. */
object StftPadding {

    /** [count] zeros followed by [signal] (Spleeter style, `count = fftSize - hop`). */
    fun leadingZeros(signal: FloatArray, count: Int): FloatArray {
        require(count >= 0) { "count must not be negative" }
        val padded = FloatArray(count + signal.size)
        signal.copyInto(padded, count)
        return padded
    }

    /** [signal] with [pad] samples reflected on each side, like torch `pad_mode = "reflect"` (edge not repeated). */
    fun reflect(signal: FloatArray, pad: Int): FloatArray {
        require(pad in 0 until signal.size) { "pad must be in 0 until ${signal.size}, was $pad" }
        val padded = FloatArray(signal.size + 2 * pad)
        signal.copyInto(padded, pad)
        for (i in 1..pad) {
            padded[pad - i] = signal[i]
            padded[pad + signal.size - 1 + i] = signal[signal.size - 1 - i]
        }
        return padded
    }
}

/** Multiplies [values] by [factor] in place (torch `normalized = true` is `1 / sqrt(fftSize)`). */
fun scaleInPlace(values: FloatArray, factor: Float) {
    for (i in values.indices) values[i] *= factor
}
