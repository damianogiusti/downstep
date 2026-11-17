package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.player.LoopZoomUiState
import com.damianogiusti.slowdowner.ui.player.PlayerControlsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Mouse gestures on the zoom of a loop, through the real canvas: the window maps pixels back to the
 * file, so a drag of a few pixels moves A or B by a few hundredths of a second.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LoopZoomGestureTest {

    // 100 s, loop 40..50 s: the zoom shows 36.5..53.5 s across 400 px, at density 1.
    private val controls = PlayerControlsState(isOpen = true, durationSeconds = 100f, isLoopOn = true, loopStart = 0.4f, loopEnd = 0.5f)
    private val zoom = LoopZoomUiState(from = 0.365f, to = 0.535f, levels = null)
    private val seeks = mutableListOf<Float>()
    private val loops = mutableListOf<Pair<Float, Float>>()
    private lateinit var scene: ImageComposeScene

    @AfterTest
    fun tearDown() {
        SwingUtilities.invokeAndWait { scene.close() }
    }

    @Test
    fun `dragging A moves only A, to the time under the pointer`() {
        show()

        drag(from = xOf(0.4f), to = xOf(0.38f))

        val (start, end) = assertNotNull(loops.singleOrNull())
        assertEquals(0.38f, start, PIXEL)
        assertEquals(0.5f, end)
        assertEquals(emptyList(), seeks)
    }

    @Test
    fun `dragging B moves only B`() {
        show()

        drag(from = xOf(0.5f), to = xOf(0.52f))

        val (moved, kept) = assertNotNull(loops.singleOrNull())
        assertEquals(0.52f, moved, PIXEL)
        assertEquals(0.4f, kept)
    }

    @Test
    fun `dragging A past the edge of the zoom stops at the edge`() {
        show()

        drag(from = xOf(0.4f), to = -50f)

        assertEquals(0.365f, assertNotNull(loops.singleOrNull()).first, PIXEL)
    }

    @Test
    fun `a click away from A and B seeks there`() {
        show()

        click(xOf(0.45f))

        assertEquals(0.45f, assertNotNull(seeks.singleOrNull()), PIXEL)
        assertNull(loops.singleOrNull())
    }

    @Test
    fun `a click on A without moving seeks instead of moving the loop`() {
        show()

        click(xOf(0.4f))

        assertEquals(0.4f, assertNotNull(seeks.singleOrNull()), PIXEL)
        assertNull(loops.singleOrNull())
    }

    private fun show() {
        SwingUtilities.invokeAndWait {
            scene = ImageComposeScene(width = WIDTH, height = 120, density = Density(1f)) {
                SlowDownerTheme(darkTheme = false) {
                    Box(Modifier.width(WIDTH.dp)) {
                        LoopZoom(
                            LoopZoomState(controls, zoom, isEnabled = true),
                            TimelineActions(onSeek = { seeks += it }, onLoopRegionChanged = { start, end -> loops += start to end }),
                        )
                    }
                }
            }
        }
        frames()
    }

    private fun drag(from: Float, to: Float) {
        send(PointerEventType.Press, from)
        // In steps, past the touch slop, as a real mouse reports it.
        for (step in 1..STEPS) send(PointerEventType.Move, from + (to - from) * step / STEPS)
        send(PointerEventType.Release, to)
        frames()
    }

    private fun click(x: Float) {
        send(PointerEventType.Press, x)
        send(PointerEventType.Release, x)
        frames()
    }

    private fun send(type: PointerEventType, x: Float) {
        SwingUtilities.invokeAndWait {
            scene.sendPointerEvent(type, Offset(x, Y))
            scene.render(++time * FRAME_NANOS)
        }
    }

    private fun frames() = repeat(3) { SwingUtilities.invokeAndWait { scene.render(++time * FRAME_NANOS) } }

    private fun xOf(fraction: Float) = (fraction - zoom.from) / (zoom.to - zoom.from) * WIDTH

    private var time = 0L

    private companion object {
        const val WIDTH = 400
        // Below the pills, on the track: what a user grabs.
        const val Y = 40f
        const val STEPS = 10
        const val FRAME_NANOS = 16_000_000L
        // One pixel of this zoom, as a fraction of the file.
        const val PIXEL = 0.17f / WIDTH
    }
}
