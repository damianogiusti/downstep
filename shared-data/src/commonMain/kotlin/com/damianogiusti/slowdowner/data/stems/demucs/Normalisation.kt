package com.damianogiusti.slowdowner.data.stems.demucs

import kotlin.math.sqrt

/** Mean and unbiased standard deviation (divisor `n - 1`, as torch's `std`). */
data class MeanStd(val mean: Double, val std: Double)

/** Statistics of the first [count] values of [values] from [offset], in `Double`. At least two values. */
fun meanAndStd(values: FloatArray, offset: Int = 0, count: Int = values.size - offset): MeanStd {
    require(count >= 2) { "need at least two values for a standard deviation, got $count" }
    var sum = 0.0
    for (i in offset until offset + count) sum += values[i]
    val mean = sum / count
    var squares = 0.0
    for (i in offset until offset + count) {
        val d = values[i] - mean
        squares += d * d
    }
    return MeanStd(mean, sqrt(squares / (count - 1)))
}

/** Statistics of the mono mix `(left + right) / 2`, the `ref` of Demucs' `separate`. */
fun monoMeanAndStd(left: FloatArray, right: FloatArray): MeanStd {
    require(left.size == right.size) { "channels differ in length" }
    require(left.size >= 2) { "need at least two samples" }
    val count = left.size
    var sum = 0.0
    for (i in 0 until count) sum += (left[i] + right[i]) * 0.5
    val mean = sum / count
    var squares = 0.0
    for (i in 0 until count) {
        val d = (left[i] + right[i]) * 0.5 - mean
        squares += d * d
    }
    return MeanStd(mean, sqrt(squares / (count - 1)))
}

/**
 * The whole-track normalisation Demucs' `separate` does around `apply_model`: both channels get
 * `(x - mean) / (std + 1e-8)` with the statistics of the mono mix, and the stems are scaled back with
 * `y * (std + 1e-8) + mean`.
 */
class TrackNormalisation(statistics: MeanStd) {
    private val mean = statistics.mean.toFloat()
    private val scale = (statistics.std + 1e-8).toFloat()

    fun normalise(value: Float): Float = (value - mean) / scale

    fun restore(value: Float): Float = value * scale + mean
}

/**
 * Normalises [values] in place as the network does inside `forward`: `(x - mean) / (1e-5 + std)` over all of
 * its elements. Returns the statistics, which the output of the network needs to be scaled back.
 */
fun normaliseInPlace(values: FloatArray, count: Int = values.size): MeanStd {
    val statistics = meanAndStd(values, 0, count)
    val mean = statistics.mean.toFloat()
    val divisor = 1e-5f + statistics.std.toFloat()
    for (i in 0 until count) values[i] = (values[i] - mean) / divisor
    return statistics
}
