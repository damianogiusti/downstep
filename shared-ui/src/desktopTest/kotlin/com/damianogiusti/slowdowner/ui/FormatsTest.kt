package com.damianogiusti.slowdowner.ui

import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.common.formatDecimal
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
    fun `clock pads the seconds`() {
        assertEquals("3:05", formatClock(185f))
    }

    /**
     * Compose Resources fills only %N$s and %N$d: anything else would show up raw, as %1$.1f did. It
     * does not unescape %% either (the export banner read "40%%"), so a literal percent is a lone %.
     */
    @Test
    fun `strings use only placeholders Compose Resources fills`() {
        for (folder in listOf("values", "values-it")) {
            val strings = File("src/commonMain/composeResources/$folder/strings.xml").readText()
            val unsupported = Regex("""%%|%(?!\d+\$[sd])(?=[\d.sdfx])""").findAll(strings)
                .map { strings.substring(maxOf(0, it.range.first - 30), minOf(strings.length, it.range.last + 10)) }
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
