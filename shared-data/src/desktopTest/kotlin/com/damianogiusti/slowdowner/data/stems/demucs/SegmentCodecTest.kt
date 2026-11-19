package com.damianogiusti.slowdowner.data.stems.demucs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class SegmentCodecTest {

    private val segmentLength = DemucsModel.SEGMENT_LENGTH

    private fun musicLikeSegment(seed: Int): FloatArray {
        // Tones only: the network never sees the Nyquist bin, so white noise would not come back (1 bin of 2049).
        val random = Random(seed)
        val phases = DoubleArray(6) { random.nextDouble() * 2 * PI }
        val frequencies = doubleArrayOf(110.0, 220.0, 880.0, 3000.0, 7000.0, 15_000.0)
        return FloatArray(2 * segmentLength) { i ->
            val n = i % segmentLength
            var value = 0.0
            for (k in frequencies.indices) value += 0.15 * sin(2 * PI * frequencies[k] * (n + 7 * (i / segmentLength)) / 44100 + phases[k])
            value.toFloat()
        }
    }

    @Test
    fun `analysis normalises both inputs`() {
        val segment = musicLikeSegment(1)
        val spectrogram = FloatArray(DemucsModel.SPECTROGRAM_FLOATS)

        val statistics = SegmentCodec().analyse(segment, spectrogram)

        for (values in listOf(spectrogram, segment)) {
            val after = meanAndStd(values)
            assertEquals(0.0, after.mean, 1e-4)
            assertEquals(1.0, after.std, 1e-3)
        }
        assertTrue(statistics.time.std > 0.1)
    }

    @Test
    fun `a tone lands in its bin of the left channel`() {
        val bin = 100
        val tone = FloatArray(2 * segmentLength) { i ->
            if (i < segmentLength) sin(2 * PI * bin * i / DemucsModel.FFT_SIZE).toFloat() else 0f
        }
        val spectrogram = FloatArray(DemucsModel.SPECTROGRAM_FLOATS)

        SegmentCodec().analyse(tone, spectrogram)

        // Layout [channel][bin][frame]: find the loudest bin over the frames in the middle of the left real part.
        val frames = DemucsModel.FRAMES
        val energy = DoubleArray(DemucsModel.FREQUENCY_BINS) { f ->
            var sum = 0.0
            for (t in 50 until 250) {
                val re = spectrogram[f * frames + t]
                val im = spectrogram[(DemucsModel.FREQUENCY_BINS + f) * frames + t]
                sum += re * re + im * im
            }
            sum
        }
        assertEquals(bin, energy.indices.maxByOrNull { energy[it] })
    }

    @Test
    fun `an identity network rebuilds the segment on both branches`() {
        val segment = musicLikeSegment(3)
        val original = segment.copyOf()
        val spectrogram = FloatArray(DemucsModel.SPECTROGRAM_FLOATS)
        val codec = SegmentCodec()

        val statistics = codec.analyse(segment, spectrogram) // segment is now xt
        val stems = DemucsModel.STEMS.size
        // The network divides by (1e-5 + std) and the host multiplies by std: an identity network must make up the
        // difference, or every stem is a little quieter (about 1e-5 / std, 1e-3 for a quiet spectrogram).
        val spectralFix = ((1e-5 + statistics.spectral.std) / statistics.spectral.std).toFloat()
        val timeFix = ((1e-5 + statistics.time.std) / statistics.time.std).toFloat()
        val spectralOutput = FloatArray(stems * DemucsModel.SPECTROGRAM_FLOATS) { spectrogram[it % DemucsModel.SPECTROGRAM_FLOATS] * spectralFix }
        val timeOutput = FloatArray(stems * 2 * segmentLength) { segment[it % (2 * segmentLength)] * timeFix }
        val destination = Array(stems * 2) { FloatArray(segmentLength) }

        codec.synthesise(spectralOutput, timeOutput, statistics, destination)

        // Each branch gives the segment back, so the sum is twice the segment. The first and last 1536 + 2048
        // samples miss the frames Demucs drops (two on each side), so they are not compared.
        var worst = 0f
        var worstAt = -1
        for (stem in 0 until stems) {
            for (channel in 0..1) {
                val out = destination[DemucsModel.channelIndex(stem, channel)]
                for (n in 2000 until 341_000) {
                    val e = abs(out[n] - 2f * original[channel * segmentLength + n])
                    if (e > worst) { worst = e; worstAt = n }
                }
            }
        }
        assertTrue("worst error $worst at $worstAt", worst < 2e-4f)
    }

    @Test
    fun `wrong sizes are refused`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SegmentCodec().analyse(FloatArray(10), FloatArray(DemucsModel.SPECTROGRAM_FLOATS))
        }
    }
}
