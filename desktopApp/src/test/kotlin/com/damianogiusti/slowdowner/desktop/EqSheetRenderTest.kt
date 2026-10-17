package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.damianogiusti.slowdowner.domain.effects.EffectsSettings
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine.Companion.SLIDER_MAX
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.effects.EffectsSheetContent
import com.damianogiusti.slowdowner.ui.effects.EffectsUiState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

/** The effects sheet with a shaped EQ, in both themes: build/screenshots/eq-dark.png and eq-light.png. */
class EqSheetRenderTest {

    private val state = EffectsUiState(
        settings = EffectsSettings(
            eq = EqCurve(
                enabled = true,
                preampDb = -2f,
                bands = listOf(
                    EqBand(60f, 4f, 0.7f),
                    EqBand(150f, 1.5f, 1f),
                    EqBand(400f, -3f, 1.4f),
                    EqBand(1_000f, 0f, 1f),
                    EqBand(2_400f, 5f, 2.5f),
                    EqBand(6_000f, -2f, 1f),
                    EqBand(15_000f, 6f, 0.7f),
                ),
            ),
        ),
        mixSlider = SLIDER_MAX / 2,
        mixRightPercent = 50,
        trackId = "t1",
    )

    @Test
    fun `the EQ graph renders in the dark theme`() {
        assertTrue(render(ThemeMode.Dark, File("build/screenshots/eq-dark.png")) > 10_000)
    }

    @Test
    fun `the EQ graph renders in the light theme`() {
        assertTrue(render(ThemeMode.Light, File("build/screenshots/eq-light.png")) > 10_000)
    }

    private fun render(theme: ThemeMode, output: File): Int {
        output.parentFile.mkdirs()
        var bytes = 0
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = 560 * 2, height = 720 * 2, density = Density(2f)) {
                SlowDownerTheme(theme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        EffectsSheetContent(
                            state = state,
                            onMixChanged = {},
                            onMixReset = {},
                            onEqToggled = {},
                            onEqReset = {},
                            onPreampChanged = {},
                            onBandChanged = { _, _ -> },
                            onBandGainReset = {},
                            onQualitySelected = {},
                        )
                    }
                }
            }
            try {
                scene.render(0)
                val png = requireNotNull(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)).bytes
                output.writeBytes(png)
                bytes = png.size
            } finally {
                scene.close()
            }
        }
        return bytes
    }
}
