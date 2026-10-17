package com.damianogiusti.slowdowner.ui.effects

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.effects.EqBand
import com.damianogiusti.slowdowner.domain.effects.EqCurve
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The plot in a 1000 × 300 px scene at density 1: the axes of [EqGraphMath] in pixels. */
@OptIn(ExperimentalComposeUiApi::class)
class EqGraphGesturesTest {

    private val width = 1_000f
    private val height = 300f
    private val start = EqCurve(enabled = true, bands = listOf(EqBand(200f, 0f, 1f), EqBand(2_000f, 6f, 2f)))

    private fun nodeOf(band: EqBand) = Offset(EqGraphMath.frequencyToX(band.frequencyHz, width), EqGraphMath.gainToY(band.gainDb, height))

    @Test
    fun `dragging a node moves its frequency sideways and its gain up and down`() {
        val curve = graph {
            val node = nodeOf(start.bands[0])
            press(node)
            move(node + Offset(width / 6, -height / 20))
            move(node + Offset(width / 3, -height / 10))
            release(node + Offset(width / 3, -height / 10))
        }

        assertEquals(2_000f, curve.bands[0].frequencyHz, 1f)
        assertEquals(3f, curve.bands[0].gainDb, 0.01f)
        assertEquals(1f, curve.bands[0].q)
        assertEquals(start.bands[1], curve.bands[1])
    }

    @Test
    fun `a drag with Shift changes the width, not the position`() {
        val curve = graph {
            val node = nodeOf(start.bands[1])
            val shift = PointerKeyboardModifiers(isShiftPressed = true)
            press(node, shift)
            move(node + Offset(200f, EqGraphMath.Q_DRAG_PIXELS_PER_DOUBLING), shift)
            release(node + Offset(200f, EqGraphMath.Q_DRAG_PIXELS_PER_DOUBLING), shift)
        }

        assertEquals(2_000f, curve.bands[1].frequencyHz)
        assertEquals(6f, curve.bands[1].gainDb)
        assertEquals(4f, curve.bands[1].q, 1e-3f)
    }

    @Test
    fun `the wheel over a node changes its width`() {
        val curve = graph {
            scroll(nodeOf(start.bands[1]), notches = 4f)
            scroll(nodeOf(start.bands[0]), notches = -4f)
        }

        assertEquals(4f, curve.bands[1].q, 1e-3f)
        assertEquals(0.5f, curve.bands[0].q, 1e-3f)
    }

    @Test
    fun `the wheel away from the nodes changes nothing`() {
        val curve = graph { scroll(Offset(900f, 20f), notches = 4f) }

        assertEquals(start, curve)
    }

    @Test
    fun `a double click on a node puts its gain back to 0 dB`() {
        val resets = mutableListOf<Int>()
        graph(onReset = { resets += it }) {
            val node = nodeOf(start.bands[1])
            press(node)
            release(node)
            press(node)
            release(node)
        }

        assertEquals(listOf(1), resets)
    }

    @Test
    fun `a node pressed again right after a drag is grabbed again, not reset`() {
        val resets = mutableListOf<Int>()
        val curve = graph(onReset = { resets += it }) {
            val node = nodeOf(start.bands[0])
            val nudged = node + Offset(0f, -height / 20) // +1.5 dB
            press(node)
            move(nudged)
            release(nudged)
            // Within the double-click time, on the node where it now is: a second nudge.
            press(nudged)
            move(nudged + Offset(0f, -height / 20))
            release(nudged + Offset(0f, -height / 20))
        }

        assertEquals(emptyList<Int>(), resets)
        assertEquals(3f, curve.bands[0].gainDb, 0.01f)
    }

    @Test
    fun `a press away from the nodes takes nothing`() {
        val curve = graph {
            press(Offset(900f, 20f))
            move(Offset(500f, 200f))
            release(Offset(500f, 200f))
        }

        assertEquals(start, curve)
    }

    private fun graph(onReset: (Int) -> Unit = {}, block: ImageComposeScene.() -> Unit): EqCurve {
        var curve by mutableStateOf(start)
        var active by mutableStateOf<Int?>(null)
        val scene = ImageComposeScene(width = width.toInt(), height = height.toInt(), density = Density(1f)) {
            SlowDownerTheme {
                EqGraphPlot(
                    curve = curve,
                    active = active,
                    onActive = { active = it },
                    onBandChanged = { index, band -> curve = curve.withBand(index, band) },
                    onBandGainReset = onReset,
                    modifier = Modifier.size(width.dp, height.dp),
                )
            }
        }
        scene.render(0)
        try {
            scene.block()
            scene.render(1_000_000_000L)
        } finally {
            scene.close()
        }
        assertTrue(curve.bands.size == start.bands.size)
        return curve
    }

    private fun ImageComposeScene.press(at: Offset, modifiers: PointerKeyboardModifiers? = null) {
        sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary, keyboardModifiers = modifiers)
        render(0)
    }

    private fun ImageComposeScene.move(to: Offset, modifiers: PointerKeyboardModifiers? = null) {
        sendPointerEvent(PointerEventType.Move, to, keyboardModifiers = modifiers)
        render(0)
    }

    private fun ImageComposeScene.release(at: Offset, modifiers: PointerKeyboardModifiers? = null) {
        sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary, keyboardModifiers = modifiers)
        render(0)
    }

    private fun ImageComposeScene.scroll(at: Offset, notches: Float) {
        sendPointerEvent(PointerEventType.Move, at)
        sendPointerEvent(PointerEventType.Scroll, at, scrollDelta = Offset(0f, notches))
        render(0)
    }
}
