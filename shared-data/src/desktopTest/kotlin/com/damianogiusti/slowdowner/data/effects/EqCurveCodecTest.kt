package com.damianogiusti.slowdowner.data.effects

import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EqCurveCodecTest {

    private val curve = EqCurve(
        enabled = true,
        preampDb = -2.5f,
        bands = listOf(EqBand(60f, 3f, 0.7f), EqBand(1_234.5f, -4.5f, 2.25f), EqBand(15_000f, 12f, 10f)),
    )

    @Test
    fun `when a curve is encoded then it decodes to the same curve`() {
        assertEquals(curve, EqCurveCodec.decode(EqCurveCodec.encode(curve)))
        assertEquals(EqCurve.FLAT, EqCurveCodec.decode(EqCurveCodec.encode(EqCurve.FLAT)))
    }

    @Test
    fun `when a curve is encoded then it is one compact versioned object`() {
        val json = EqCurveCodec.encode(EqCurve(enabled = true, preampDb = -2f, bands = listOf(EqBand(60f, 3f, 0.7f))))

        assertEquals("""{"v":1,"on":true,"pre":-2.0,"b":[[60.0,3.0,0.7]]}""", json)
    }

    @Test
    fun `when there is no text or no object then nothing is read`() {
        listOf(null, "", "   ", "{oops", "[1,2,3]", "42", "\"curve\"").forEach {
            assertNull("read $it", EqCurveCodec.decode(it))
        }
    }

    @Test
    fun `when keys are missing then the switch is off, the preamp 0 dB and the bands the default ones`() {
        assertEquals(EqCurve(enabled = false, preampDb = 0f, bands = EqCurve.DEFAULT_BANDS), EqCurveCodec.decode("{}"))
    }

    @Test
    fun `when values are of the wrong kind then they are ignored`() {
        val decoded = EqCurveCodec.decode("""{"on":"yes","pre":"loud","b":"none","extra":[1]}""")

        assertEquals(EqCurve(enabled = false, preampDb = 0f, bands = EqCurve.DEFAULT_BANDS), decoded)
    }

    @Test
    fun `when a band is malformed then only that band is dropped`() {
        val decoded = EqCurveCodec.decode(
            """{"v":1,"on":true,"pre":0,"b":[[100,2,1],[200],"x",[300,"a",1],[400,1,2,9],[500,-3]]}""",
        )!!

        assertEquals(listOf(EqBand(100f, 2f, 1f), EqBand(500f, -3f, EqBand.DEFAULT_Q)), decoded.bands)
    }

    @Test
    fun `when values are out of range then they are clamped`() {
        val decoded = EqCurveCodec.decode("""{"on":true,"pre":99,"b":[[5,40,0.01],[90000,-40,50]]}""")!!

        assertEquals(EqCurve.MAX_PREAMP_DB, decoded.preampDb)
        assertEquals(
            listOf(EqBand(EqBand.MIN_FREQUENCY_HZ, EqBand.MAX_GAIN_DB, EqBand.MIN_Q), EqBand(EqBand.MAX_FREQUENCY_HZ, EqBand.MIN_GAIN_DB, EqBand.MAX_Q)),
            decoded.bands,
        )
    }

    @Test
    fun `when there are more bands than the engine has then the extra ones are dropped`() {
        val many = (1..12).joinToString(",", "[", "]") { "[${it * 100},1,1]" }

        val decoded = EqCurveCodec.decode("""{"on":true,"b":$many}""")!!

        assertEquals(EqCurve.MAX_BANDS, decoded.bands.size)
        assertEquals(800f, decoded.bands.last().frequencyHz)
    }

    @Test
    fun `when a future version adds keys then the curve still reads`() {
        val decoded = EqCurveCodec.decode("""{"v":7,"on":true,"pre":1,"b":[[1000,2,1]],"shape":"tilt"}""")!!

        assertEquals(EqCurve(true, 1f, listOf(EqBand(1_000f, 2f, 1f))), decoded)
        assertFalse(decoded.isFlat)
    }
}
