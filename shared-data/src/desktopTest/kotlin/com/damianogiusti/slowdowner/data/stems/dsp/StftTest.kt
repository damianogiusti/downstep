package com.damianogiusti.slowdowner.data.stems.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

class StftTest {

    private val fftSize = 4096
    private val hop = 1024
    private val window = periodicHannWindow(fftSize)

    @Test
    fun `window is the periodic Hann`() {
        assertEquals(0f, window[0], 0f)
        assertEquals(1f, window[fftSize / 2], 1e-7f)
        assertEquals(0.5f - 0.5f * cos(2 * PI / fftSize).toFloat(), window[1], 1e-6f)
        // Periodic: the last sample is not zero, and it is the mirror of sample 1.
        assertEquals(window[1], window[fftSize - 1], 1e-7f)
    }

    @Test
    fun `frame count and signal length follow the hop`() {
        val stft = Stft(fftSize, hop, window)

        assertEquals(0, stft.frameCount(fftSize - 1))
        assertEquals(1, stft.frameCount(fftSize))
        assertEquals(1, stft.frameCount(fftSize + hop - 1))
        assertEquals(2, stft.frameCount(fftSize + hop))
        assertEquals(fftSize + 3 * hop, stft.signalLength(4))
    }

    @Test
    fun `reflect padding mirrors without repeating the edge`() {
        val padded = StftPadding.reflect(floatArrayOf(1f, 2f, 3f, 4f, 5f), 2)

        assertArrayEquals(floatArrayOf(3f, 2f, 1f, 2f, 3f, 4f, 5f, 4f, 3f), padded, 0f)
    }

    @Test
    fun `leading zeros are prepended`() {
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 2f), StftPadding.leadingZeros(floatArrayOf(1f, 2f), 2), 0f)
    }

    @Test
    fun `spleeter style round trip leaves a residual below minus 120 dB`() {
        val signal = noise(50_000, seed = 1)
        val padded = StftPadding.leadingZeros(signal, fftSize - hop) + FloatArray(fftSize) // tail so the last samples are covered
        val stft = Stft(fftSize, hop, window)
        val spectra = stft.forward(padded)
        val frames = stft.frameCount(padded.size)
        val rebuilt = FloatArray(stft.signalLength(frames))

        stft.inverse(spectra, frames, rebuilt)

        val db = residualDb(signal, rebuilt, offset = fftSize - hop)
        println("Stft Spleeter-style round trip residual = $db dB")
        assertTrue("residual $db dB", db < -120.0)
    }

    @Test
    fun `torch style round trip leaves a residual below minus 120 dB`() {
        val signal = noise(50_000 + 123, seed = 2)
        val stft = Stft(fftSize, hop, window)
        val padded = StftPadding.reflect(signal, fftSize / 2)
        val spectra = stft.forward(padded)
        val frames = stft.frameCount(padded.size)
        scaleInPlace(spectra, 1f / sqrt(fftSize.toFloat()))
        scaleInPlace(spectra, sqrt(fftSize.toFloat()))
        val rebuilt = FloatArray(stft.signalLength(frames))

        stft.inverse(spectra, frames, rebuilt)

        // Frames cover 1 + length / hop of them, so the tail after the last whole hop is not rebuilt.
        val covered = (signal.size / hop) * hop
        val db = residualDb(signal.copyOf(covered), rebuilt, offset = fftSize / 2)
        println("Stft torch-style round trip residual = $db dB")
        assertTrue("residual $db dB", db < -120.0)
    }

    @Test
    fun `spleeter style matches the numpy reference`() {
        val signal = reference("signal.bin")
        val padded = StftPadding.leadingZeros(signal, fftSize - hop)
        val stft = Stft(fftSize, hop, window)
        val spectra = stft.forward(padded)
        val expected = reference("spleeter_spec.bin")

        val db = errorDb(expected, spectra)
        println("Spleeter spectrum vs numpy: ${spectra.size / stft.frameStride} frames, error = $db dB")
        assertEquals(expected.size, spectra.size)
        assertTrue("spectrum error $db dB", db < -110.0)

        val frames = stft.frameCount(padded.size)
        val rebuilt = FloatArray(stft.signalLength(frames))
        stft.inverse(expected, frames, rebuilt)
        // The last hop is covered by the last frame alone, where the squared window sum goes to zero and the
        // division amplifies float noise (the reference divides in double): it is left out of the comparison.
        val inverse = rebuilt.copyOfRange(fftSize - hop, rebuilt.size - hop)
        val inverseDb = errorDb(reference("spleeter_istft.bin").copyOf(inverse.size), inverse)
        println("Spleeter inverse vs numpy (without the last hop): error = $inverseDb dB")
        assertTrue("inverse error $inverseDb dB", inverseDb < -110.0)
    }

    @Test
    fun `torch style matches torch stft and istft`() {
        val signal = reference("signal.bin")
        val stft = Stft(fftSize, hop, window)
        val padded = StftPadding.reflect(signal, fftSize / 2)
        val spectra = stft.forward(padded)
        scaleInPlace(spectra, 1f / sqrt(fftSize.toFloat()))
        val expected = reference("demucs_spec.bin")

        val db = errorDb(expected, spectra)
        println("Demucs spectrum vs torch.stft: ${spectra.size / stft.frameStride} frames, error = $db dB")
        assertEquals(expected.size, spectra.size)
        assertTrue("spectrum error $db dB", db < -110.0)

        val frames = stft.frameCount(padded.size)
        val input = expected.copyOf()
        scaleInPlace(input, sqrt(fftSize.toFloat()))
        val rebuilt = FloatArray(stft.signalLength(frames))
        stft.inverse(input, frames, rebuilt)
        val inverse = rebuilt.copyOfRange(fftSize / 2, rebuilt.size - fftSize / 2)
        val expectedInverse = reference("demucs_istft.bin")
        val inverseDb = errorDb(expectedInverse, inverse)
        println("Demucs inverse vs torch.istft: ${inverse.size} samples, error = $inverseDb dB")
        assertEquals(expectedInverse.size, inverse.size)
        assertTrue("inverse error $inverseDb dB", inverseDb < -110.0)
    }

    @Test
    fun `performance of the forward and inverse transforms is printed`() {
        val seconds = 281
        val samples = seconds * 44_100
        val stft = Stft(fftSize, hop, window)
        val padded = StftPadding.reflect(noise(samples, seed = 3), fftSize / 2)
        val frames = stft.frameCount(padded.size)
        val spectra = FloatArray(frames * stft.frameStride)
        val rebuilt = FloatArray(stft.signalLength(frames))
        // Warm up the JIT on a short run, as the app would be warm after the first track.
        repeat(3) { stft.forward(padded, 200_000, spectra); stft.inverse(spectra, stft.frameCount(200_000), rebuilt) }

        val forwardStart = System.nanoTime()
        repeat(2) { stft.forward(padded, padded.size, spectra) } // stereo: two channels
        val forwardSeconds = (System.nanoTime() - forwardStart) / 1e9

        val inverseStart = System.nanoTime()
        repeat(12) { stft.inverse(spectra, frames, rebuilt) } // 6 stems x 2 channels
        val inverseSeconds = (System.nanoTime() - inverseStart) / 1e9

        println("PERF STFT of ${seconds}s stereo: %.2f s".format(forwardSeconds))
        println("PERF iSTFT of 6 stems x 2 channels (${seconds}s): %.2f s".format(inverseSeconds))
    }

    private fun noise(size: Int, seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray(size) { random.nextFloat() * 2f - 1f }
    }

    private fun residualDb(expected: FloatArray, rebuilt: FloatArray, offset: Int): Double {
        val actual = rebuilt.copyOfRange(offset, offset + expected.size)
        return errorDb(expected, actual)
    }

    /** 20 log10 of rms(error) / rms(expected). */
    private fun errorDb(expected: FloatArray, actual: FloatArray): Double {
        require(expected.size == actual.size) { "sizes ${expected.size} vs ${actual.size}" }
        var error = 0.0
        var energy = 0.0
        for (i in expected.indices) {
            val d = (expected[i] - actual[i]).toDouble()
            error += d * d
            energy += expected[i].toDouble() * expected[i]
        }
        return 10 * log10(maxOf(error, 1e-300) / energy)
    }

    private fun reference(name: String): FloatArray {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/stems/dsp/$name")) { "missing resource $name" }
            .use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}
