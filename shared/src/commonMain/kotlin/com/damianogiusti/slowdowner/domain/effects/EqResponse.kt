package com.damianogiusti.slowdowner.domain.effects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the EQ does to each frequency, in dB: the magnitude of the engine's own filters (RBJ peaking
 * biquads, engine/src/Biquad.h), so the curve drawn is the curve heard. Pure maths, for the graph
 * and the tests.
 */
object EqResponse {
    /** The rate the curve is drawn at: the usual device rate. Below 10 kHz any rate looks the same. */
    const val DEFAULT_SAMPLE_RATE = 48_000.0

    /** One bell's gain at [frequencyHz]: |H(e^jw)| of its biquad. */
    fun bandMagnitudeDb(band: EqBand, frequencyHz: Double, sampleRate: Double = DEFAULT_SAMPLE_RATE): Double {
        if (band.gainDb == 0f) return 0.0
        val centre = band.frequencyHz.toDouble().coerceIn(20.0, maxOf(20.0, sampleRate * 0.45))
        val a = 10.0.pow(band.gainDb / 40.0)
        val w0 = 2 * PI * centre / sampleRate
        val alpha = sin(w0) / (2 * band.q)
        val cosw0 = cos(w0)
        // Coefficients normalised by a0.
        val a0 = 1 + alpha / a
        val b0 = (1 + alpha * a) / a0
        val b1 = -2 * cosw0 / a0
        val b2 = (1 - alpha * a) / a0
        val a1 = b1
        val a2 = (1 - alpha / a) / a0
        // |b0 + b1 z^-1 + b2 z^-2| / |1 + a1 z^-1 + a2 z^-2| at z = e^jw.
        val w = 2 * PI * frequencyHz / sampleRate
        val c1 = cos(w)
        val s1 = sin(w)
        val c2 = cos(2 * w)
        val s2 = sin(2 * w)
        val numerator = sqrt((b0 + b1 * c1 + b2 * c2).squared() + (b1 * s1 + b2 * s2).squared())
        val denominator = sqrt((1 + a1 * c1 + a2 * c2).squared() + (a1 * s1 + a2 * s2).squared())
        return 20 * log10(numerator / denominator)
    }

    /**
     * The whole EQ's gain at [frequencyHz]: the bands in series add up in dB. With [withPreamp] the
     * preamp is added too. An EQ that is off is 0 dB everywhere.
     */
    fun curveMagnitudeDb(
        curve: EqCurve,
        frequencyHz: Double,
        withPreamp: Boolean = false,
        sampleRate: Double = DEFAULT_SAMPLE_RATE,
    ): Double {
        if (!curve.enabled) return 0.0
        val bands = curve.bands.sumOf { bandMagnitudeDb(it, frequencyHz, sampleRate) }
        return if (withPreamp) bands + curve.preampDb else bands
    }

    private fun Double.squared() = this * this
}
