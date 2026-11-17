package com.damianogiusti.slowdowner.ui

import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatDecimal
import com.damianogiusti.slowdowner.ui.common.formatPitch
import com.damianogiusti.slowdowner.ui.common.formatPreciseClock
import com.damianogiusti.slowdowner.ui.common.formatSignedDecimal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormatsTest {

    @Test
    fun `decimals use a dot and a real minus`() {
        assertEquals("2.4", formatDecimal(2.4f))
        assertEquals("15.0", formatDecimal(15f))
        assertEquals("+2.5", formatSignedDecimal(2.5f))
        assertEquals("−1.0", formatSignedDecimal(-1f))
        assertEquals("0.0", formatSignedDecimal(0.04f))
        assertEquals("−0.1", formatSignedDecimal(-0.06f))
    }

    @Test
    fun `decimals take the separator of the language`() {
        assertEquals("2,4", formatDecimal(2.4f, ","))
        assertEquals("−1,0", formatSignedDecimal(-1f, ","))
    }

    @Test
    fun `pitch shows whole semitones without decimals`() {
        assertEquals("+2 st", formatPitch(2f))
        assertEquals("−2 st", formatPitch(-2f))
        assertEquals("0 st", formatPitch(0f))
        assertEquals("0 st", formatPitch(0.001f))
    }

    @Test
    fun `pitch with cents shows two decimals`() {
        assertEquals("+2.15 st", formatPitch(2.15f))
        assertEquals("−0.30 st", formatPitch(-0.3f))
        assertEquals("+0.05 st", formatPitch(0.05f))
        assertEquals("+2.20 st", formatPitch(2.1999998f))
    }

    @Test
    fun `pitch takes the separator of the language`() {
        assertEquals("+2,15 st", formatPitch(2.15f, ","))
        assertEquals("−0,30 st", formatPitch(-0.3f, ","))
        assertEquals("+2 st", formatPitch(2f, ","))
    }

    @Test
    fun `clock pads the seconds`() {
        assertEquals("3:05", formatClock(185f))
    }

    @Test
    fun `precise clock shows hundredths in the language's separator`() {
        assertEquals("1:05.25", formatPreciseClock(65.25f))
        assertEquals("0:00,07", formatPreciseClock(0.07f, ","))
        assertEquals("1:00.00", formatPreciseClock(59.999f))
    }

    /**
     * Compose Resources fills only %N$s and %N$d and leaves every other % as it is: %1$.1f showed up
     * raw, and %% showed two percent signs. A literal percent sign is a single %, not followed by what
     * reads as a format.
     */
    @Test
    fun `strings use only placeholders Compose Resources fills`() {
        for (folder in listOf("values", "values-it")) {
            val strings = File("src/commonMain/composeResources/$folder/strings.xml").readText()
            val withoutPlaceholders = strings.replace(Regex("""%\d+\$[sd]"""), "")
            val unsupported = Regex("""%(?=[%\d$.+\-a-zA-Z])""").findAll(withoutPlaceholders)
                .map {
                    val end = minOf(withoutPlaceholders.length, it.range.last + 10)
                    withoutPlaceholders.substring(maxOf(0, it.range.first - 30), end)
                }
                .toList()
            assertTrue(unsupported.isEmpty(), "unsupported placeholders in $folder: $unsupported")
        }
    }

    /** Every Italian string must exist in English too, or English users would hit a missing resource. */
    @Test
    fun `every Italian string has an English default`() {
        val names = { folder: String ->
            Regex("""<(?:string|plurals) name="([^"]+)"""")
                .findAll(File("src/commonMain/composeResources/$folder/strings.xml").readText())
                .map { it.groupValues[1] }
                .toSet()
        }
        val missing = names("values-it") - names("values")
        assertTrue(missing.isEmpty(), "missing in values: $missing")
    }
}
