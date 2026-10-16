package com.damianogiusti.slowdowner.ui.player.components.waveform

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.ui.player.LoopRegionModel
import com.damianogiusti.slowdowner.ui.player.OverviewWaveformState
import com.damianogiusti.slowdowner.ui.player.Peaks
import com.damianogiusti.slowdowner.ui.player.WaveformActions
import com.damianogiusti.slowdowner.ui.player.components.OverviewWaveform
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class WaveformGesturesTest {

    // 1048 px wide: the axis is 24..1024, so x = 24 + fraction × 1000.
    private val active = LoopRegionModel(id = 7, start = 0.2f, end = 0.4f, colorIndex = 2, isActive = true)
    private val state = OverviewWaveformState(
        isOpen = true,
        durationSeconds = 100f,
        peaks = Peaks(0f, 1f, FloatArray(64) { 0.5f }),
        regions = listOf(active),
        active = active,
        isLoopOn = true,
        enabled = true,
    )

    @Test
    fun `a click seeks where it lands`() {
        val seeks = mutableListOf<Float>()
        scene(state, WaveformActions(onSeek = { seeks += it }, onLoopBoundsChanged = { _, _ -> })) {
            click(Offset(724f, 100f))
        }
        assertEquals(1, seeks.size)
        assertEquals(0.7f, seeks.single(), 1e-4f)
    }

    @Test
    fun `dragging B commits once, on release`() {
        val commits = mutableListOf<Pair<Long, LoopBounds>>()
        val seeks = mutableListOf<Float>()
        scene(state, WaveformActions(onSeek = { seeks += it }, onLoopBoundsChanged = { id, bounds -> commits += id to bounds })) {
            press(Offset(426f, 100f))
            move(Offset(480f, 100f))
            move(Offset(524f, 100f))
            assertTrue(commits.isEmpty(), "committed during the drag")
            release(Offset(524f, 100f))
        }
        assertTrue(seeks.isEmpty(), "a drag is not a seek")
        assertEquals(1, commits.size)
        assertEquals(7L, commits.single().first)
        assertEquals(0.2f, commits.single().second.start)
        assertEquals(0.5f, commits.single().second.end, 1e-4f)
    }

    @Test
    fun `A cannot be dragged past B`() {
        val commits = mutableListOf<LoopBounds>()
        scene(state, WaveformActions(onSeek = {}, onLoopBoundsChanged = { _, bounds -> commits += bounds })) {
            press(Offset(224f, 100f))
            move(Offset(900f, 100f))
            release(Offset(900f, 100f))
        }
        val bounds = commits.single()
        assertEquals(0.4f, bounds.end)
        assertTrue(bounds.start < bounds.end)
    }

    @Test
    fun `nothing happens while disabled`() {
        val seeks = mutableListOf<Float>()
        scene(state.copy(enabled = false), WaveformActions(onSeek = { seeks += it }, onLoopBoundsChanged = { _, _ -> error("drag") })) {
            click(Offset(724f, 100f))
            press(Offset(426f, 100f))
            move(Offset(524f, 100f))
            release(Offset(524f, 100f))
        }
        assertTrue(seeks.isEmpty())
    }

    private fun scene(state: OverviewWaveformState, actions: WaveformActions, block: ImageComposeScene.() -> Unit) {
        val scene = ImageComposeScene(width = 1048, height = 200, density = Density(1f)) {
            SlowDownerTheme { OverviewWaveform(state, { 0f }, actions) }
        }
        scene.render(0)
        try {
            scene.block()
            scene.render(1_000_000_000L)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.press(at: Offset) {
        sendPointerEvent(PointerEventType.Press, at, button = PointerButton.Primary)
        render(0)
    }

    private fun ImageComposeScene.move(to: Offset) {
        sendPointerEvent(PointerEventType.Move, to)
        render(0)
    }

    private fun ImageComposeScene.release(at: Offset) {
        sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
        render(0)
    }

    private fun ImageComposeScene.click(at: Offset) {
        press(at)
        release(at)
    }
}
