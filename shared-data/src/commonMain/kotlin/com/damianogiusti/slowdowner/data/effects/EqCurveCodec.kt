package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.put

/**
 * An [EqCurve] as one compact JSON string, for a column (track_practice_state.eq_curve,
 * practice_presets.eq_curve) or a preference (eq_curve):
 *
 * `{"v":1,"on":true,"pre":-2.0,"b":[[60.0,3.0,0.7],[1000.0,-4.5,2.0]]}`
 *
 * One `[frequency Hz, gain dB, Q]` triple per band. Reading is tolerant: unknown keys are ignored, a
 * missing switch is off and a missing preamp 0 dB, a band that is not three numbers is dropped (a
 * missing Q is 1), values are clamped, and a curve left without bands gets the default ones. Only
 * text that is not a JSON object at all reads as nothing.
 */
object EqCurveCodec {
    const val VERSION = 1

    private const val KEY_VERSION = "v"
    private const val KEY_ENABLED = "on"
    private const val KEY_PREAMP = "pre"
    private const val KEY_BANDS = "b"

    fun encode(curve: EqCurve): String {
        val clean = curve.sanitised()
        return buildJsonObject {
            put(KEY_VERSION, VERSION)
            put(KEY_ENABLED, clean.enabled)
            put(KEY_PREAMP, clean.preampDb)
            put(KEY_BANDS, buildJsonArray {
                clean.bands.forEach { band ->
                    add(buildJsonArray {
                        add(JsonPrimitive(band.frequencyHz))
                        add(JsonPrimitive(band.gainDb))
                        add(JsonPrimitive(band.q))
                    })
                }
            })
        }.toString()
    }

    /** Null for null, blank or unreadable text: the caller falls back (the global EQ, the legacy keys). */
    fun decode(json: String?): EqCurve? {
        if (json.isNullOrBlank()) return null
        val root = try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        val enabled = (root[KEY_ENABLED] as? JsonPrimitive)?.booleanOrNull ?: false
        val preamp = (root[KEY_PREAMP] as? JsonPrimitive)?.floatOrNull ?: 0f
        val bands = (root[KEY_BANDS] as? JsonArray).orEmpty().mapNotNull(::band)
        return EqCurve(enabled, preamp, bands).sanitised()
    }

    private fun band(element: kotlinx.serialization.json.JsonElement): EqBand? {
        val values = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.floatOrNull } ?: return null
        if (values.size !in 2..3 || values.any { it == null || !it.isFinite() }) return null
        return EqBand(values[0]!!, values[1]!!, values.getOrNull(2) ?: EqBand.DEFAULT_Q)
    }
}
