package com.damianogiusti.slowdowner.data.stems.demucs

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class NormalisationTest {

    @Test
    fun `std is unbiased`() {
        val statistics = meanAndStd(floatArrayOf(1f, 2f, 3f, 4f))

        assertEquals(2.5, statistics.mean, 1e-12)
        assertEquals(sqrt(5.0 / 3.0), statistics.std, 1e-12)
    }

    @Test
    fun `statistics of a range ignore the rest`() {
        val statistics = meanAndStd(floatArrayOf(100f, 1f, 3f, 100f), offset = 1, count = 2)

        assertEquals(2.0, statistics.mean, 1e-12)
        assertEquals(sqrt(2.0), statistics.std, 1e-12)
    }

    @Test
    fun `mono statistics are those of the mean of the channels`() {
        val left = floatArrayOf(1f, 3f, 5f, 7f)
        val right = floatArrayOf(3f, 1f, 1f, 3f)

        val statistics = monoMeanAndStd(left, right)

        // mono = 2, 2, 3, 5
        assertEquals(3.0, statistics.mean, 1e-12)
        assertEquals(sqrt(((1 + 1 + 0 + 4) / 3.0)), statistics.std, 1e-12)
    }

    @Test
    fun `the track normalisation is undone by restore`() {
        val random = Random(1)
        val normalisation = TrackNormalisation(MeanStd(0.01, 0.2))

        repeat(100) {
            val value = random.nextFloat() * 2 - 1
            assertEquals(value, normalisation.restore(normalisation.normalise(value)), 1e-6f)
        }
        assertEquals(0f, normalisation.normalise(0.01f), 1e-9f)
    }

    @Test
    fun `network normalisation gives zero mean and a std just under one`() {
        val random = Random(2)
        val values = FloatArray(5000) { random.nextFloat() * 6f + 2f }

        val statistics = normaliseInPlace(values)

        val after = meanAndStd(values)
        assertEquals(0.0, after.mean, 1e-5)
        // (x - mean) / (1e-5 + std): the std becomes std / (1e-5 + std).
        assertEquals(statistics.std / (1e-5 + statistics.std), after.std, 1e-5)
    }
}
