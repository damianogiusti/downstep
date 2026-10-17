package com.damianogiusti.slowdowner.domain.effects

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EqCurveTest {

    private val legacyGains = listOf(3f, 1.5f, 0f, -2f, 0f, 4.5f, 6f)

    @Test
    fun `when the first EQ is migrated then each gain becomes a bell at its frequency`() {
        val curve = EqCurve.fromLegacyGains(enabled = true, preampDb = -2f, gainsDb = legacyGains)

        assertTrue(curve.enabled)
        assertEquals(-2f, curve.preampDb)
        assertEquals(EqCurve.DEFAULT_FREQUENCIES_HZ, curve.bands.map { it.frequencyHz })
        assertEquals(legacyGains, curve.bands.map { it.gainDb })
        // The middle five were already Q 1 bells; the end shelves become broad bells.
        assertEquals(listOf(0.7f, 1f, 1f, 1f, 1f, 1f, 0.7f), curve.bands.map { it.q })
    }

    @Test
    fun `when a single old band is migrated then it keeps its gain at its frequency`() {
        EqCurve.DEFAULT_FREQUENCIES_HZ.forEachIndexed { index, hz ->
            val gains = List(7) { if (it == index) 6f else 0f }
            val curve = EqCurve.fromLegacyGains(true, 0f, gains)

            assertNear(6.0, EqResponse.curveMagnitudeDb(curve, hz.toDouble()), 0.01, "band $index at $hz Hz")
        }
    }

    @Test
    fun `when a flat old EQ is migrated then it stays exactly flat`() {
        val curve = EqCurve.fromLegacyGains(enabled = true, preampDb = 0f, gainsDb = List(7) { 0f })

        assertTrue(curve.isFlat)
        EqResponse.let { response ->
            listOf(20.0, 60.0, 1_000.0, 15_000.0).forEach { assertEquals(0.0, response.curveMagnitudeDb(curve, it)) }
        }
    }

    @Test
    fun `when the old gains have the wrong size then the bands are flat`() {
        val curve = EqCurve.fromLegacyGains(enabled = true, preampDb = 1f, gainsDb = listOf(1f, 2f))

        assertEquals(EqCurve.DEFAULT_BANDS, curve.bands)
        assertTrue(curve.enabled)
    }

    @Test
    fun `when old gains are out of range then they are clamped`() {
        val curve = EqCurve.fromLegacyGains(enabled = false, preampDb = 40f, gainsDb = List(7) { -99f })

        assertEquals(EqCurve.MAX_PREAMP_DB, curve.preampDb)
        assertTrue(curve.bands.all { it.gainDb == EqBand.MIN_GAIN_DB })
    }

    @Test
    fun `when a curve is sanitised then it has between one and eight bands in range`() {
        val many = EqCurve(true, Float.NaN, List(12) { EqBand(Float.NaN, 99f, 0f) }).sanitised()
        val none = EqCurve(true, 0f, emptyList()).sanitised()

        assertEquals(EqCurve.MAX_BANDS, many.bands.size)
        assertEquals(EqBand(1_000f, EqBand.MAX_GAIN_DB, EqBand.MIN_Q), many.bands.first())
        assertEquals(0f, many.preampDb)
        assertEquals(EqCurve.DEFAULT_BANDS, none.bands)
    }

    @Test
    fun `when a band is replaced then it is clamped and the others stay`() {
        val curve = EqCurve().withBand(2, EqBand(30_000f, 3f, 20f))

        assertEquals(EqBand(EqBand.MAX_FREQUENCY_HZ, 3f, EqBand.MAX_Q), curve.bands[2])
        assertEquals(EqCurve.DEFAULT_BANDS[1], curve.bands[1])
        assertEquals(curve, curve.withBand(9, EqBand(100f, 1f)))
    }

    @Test
    fun `when a bell is measured then the centre has its gain and the response matches the cookbook`() {
        for (band in listOf(EqBand(1_000f, 6f, 1f), EqBand(100f, -9f, 0.7f), EqBand(4_000f, 12f, 4f), EqBand(250f, -12f, 0.3f))) {
            assertNear(band.gainDb.toDouble(), EqResponse.bandMagnitudeDb(band, band.frequencyHz.toDouble()), 1e-6, "centre of $band")
            for (ratio in listOf(0.25, 0.5, 0.8, 1.25, 2.0, 3.0)) {
                val at = band.frequencyHz * ratio
                assertNear(cookbookDb(band, at, 48_000.0), EqResponse.bandMagnitudeDb(band, at), 1e-6, "$band at $at Hz")
            }
        }
    }

    @Test
    fun `when Q grows then the bell narrows`() {
        val wide = EqResponse.bandMagnitudeDb(EqBand(1_000f, 12f, 0.5f), 2_000.0)
        val narrow = EqResponse.bandMagnitudeDb(EqBand(1_000f, 12f, 4f), 2_000.0)

        assertTrue(wide > narrow + 3, "Q 0.5 gave $wide dB an octave away, Q 4 $narrow dB")
        assertNear(0.0, EqResponse.bandMagnitudeDb(EqBand(1_000f, 12f, 8f), 10_000.0), 0.1, "far from a narrow bell")
    }

    @Test
    fun `when bands are combined then their gains add up, and off is flat`() {
        val curve = EqCurve(true, -3f, listOf(EqBand(200f, 6f, 1f), EqBand(5_000f, -6f, 2f)))
        val sum = EqResponse.bandMagnitudeDb(curve.bands[0], 1_000.0) + EqResponse.bandMagnitudeDb(curve.bands[1], 1_000.0)

        assertNear(sum, EqResponse.curveMagnitudeDb(curve, 1_000.0), 1e-9, "sum")
        assertNear(sum - 3, EqResponse.curveMagnitudeDb(curve, 1_000.0, withPreamp = true), 1e-9, "with the preamp")
        assertEquals(0.0, EqResponse.curveMagnitudeDb(curve.copy(enabled = false), 1_000.0))
        assertFalse(curve.isFlat)
        assertTrue(curve.copy(enabled = false).isFlat)
    }

    /** The RBJ cookbook peaking EQ, evaluated independently: complex arithmetic on raw coefficients. */
    private fun cookbookDb(band: EqBand, at: Double, rate: Double): Double {
        val a = 10.0.pow(band.gainDb / 40.0)
        val w0 = 2 * PI * band.frequencyHz / rate
        val alpha = sin(w0) / (2 * band.q)
        val w = 2 * PI * at / rate
        fun magnitude(c0: Double, c1: Double, c2: Double): Double {
            // c0 + c1 e^-jw + c2 e^-2jw
            val re = c0 + c1 * cos(w) + c2 * cos(2 * w)
            val im = -(c1 * sin(w) + c2 * sin(2 * w))
            return sqrt(re * re + im * im)
        }
        val numerator = magnitude(1 + alpha * a, -2 * cos(w0), 1 - alpha * a)
        val denominator = magnitude(1 + alpha / a, -2 * cos(w0), 1 - alpha / a)
        return 20 * log10(numerator / denominator)
    }

    private fun assertNear(expected: Double, actual: Double, tolerance: Double, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")
}
