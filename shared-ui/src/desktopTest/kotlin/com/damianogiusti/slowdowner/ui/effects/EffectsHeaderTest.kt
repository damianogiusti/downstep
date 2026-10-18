package com.damianogiusti.slowdowner.ui.effects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The EQ and mix headers: Reset is a button of its own, the switch says On or Off. */
@OptIn(ExperimentalTestApi::class)
class EffectsHeaderTest {

    private val calls = mutableListOf<String>()
    private val flat = EffectsUiState(settings = EffectsSettings(eq = EqCurve(enabled = true)), mixSlider = SLIDER_MAX / 2, mixRightPercent = 50)
    private val shaped = flat.copy(
        settings = EffectsSettings(eq = EqCurve(enabled = true, preampDb = -2f).withBand(0, EqCurve.DEFAULT_BANDS[0].copy(gainDb = 4f))),
        mixSlider = SLIDER_MAX / 4,
        mixRightPercent = 25,
    )

    @Test
    fun `when the curve is flat then the EQ Reset is off and so is the centered mix's`() = sheet(flat) {
        onNodeWithTag(EffectsTags.EQ_RESET).assertIsNotEnabled()
        onNodeWithTag(EffectsTags.MIX_RESET).assertIsNotEnabled()
    }

    @Test
    fun `when the curve is shaped then Reset resets it and leaves the switch alone`() = sheet(shaped) {
        onNodeWithTag(EffectsTags.EQ_RESET).assertIsEnabled().performClick()
        onNodeWithTag(EffectsTags.MIX_RESET).assertIsEnabled().performClick()
        waitForIdle()
        assertEquals(listOf("eqReset", "mixReset"), calls)
    }

    @Test
    fun `the switch is labelled with its state and toggles the EQ, not Reset`() {
        var state by mutableStateOf(shaped.copy(settings = shaped.settings.copy(eq = shaped.settings.eq.copy(enabled = false))))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                SlowDownerTheme {
                    content(state) { enabled ->
                        calls += "toggled $enabled"
                        state = state.copy(settings = state.settings.copy(eq = state.settings.eq.copy(enabled = enabled)))
                    }
                }
            }
            onNodeWithContentDescription("Equalizer on")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
                .assertIsOff()
                .assertTextContains("Off")
                .performClick()
            waitForIdle()
            onNodeWithTag(EffectsTags.EQ_SWITCH).assertIsOn().assertTextContains("On")
            assertEquals(listOf("toggled true"), calls)
        }
    }

    private fun sheet(state: EffectsUiState, block: androidx.compose.ui.test.DesktopComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { SlowDownerTheme { content(state) { calls += "toggled $it" } } }
            block()
        }

    @androidx.compose.runtime.Composable
    private fun content(state: EffectsUiState, onEqToggled: (Boolean) -> Unit) {
        EffectsSheetContent(
            state = state,
            onMixChanged = {},
            onMixReset = { calls += "mixReset" },
            onEqToggled = onEqToggled,
            onEqReset = { calls += "eqReset" },
            onPreampChanged = {},
            onBandChanged = { _, _ -> },
            onBandGainReset = {},
            onQualitySelected = {},
        )
    }

    private companion object {
        const val WIDTH = 560
        const val HEIGHT = 900
    }
}
