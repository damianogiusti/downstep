package com.damianogiusti.slowdowner.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import com.damianogiusti.slowdowner.ui.player.LoopChipActions
import com.damianogiusti.slowdowner.ui.player.LoopChipModel
import com.damianogiusti.slowdowner.ui.player.LoopChipsState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** With more loops than fit, the strip scrolls with the wheel, shows the active chip and keeps "+ New loop". */
@OptIn(ExperimentalTestApi::class)
class LoopChipsScrollTest {

    private var newLoops = 0
    private val actions = LoopChipActions(
        onSelect = {},
        onNewLoop = { newLoops++ },
        onRename = { _, _ -> },
        onDelete = {},
        onSuggestionAccepted = {},
        onSuggestionDismissed = {},
    )

    @Test
    fun `when the loops overflow then the scrollbar shows and New loop stays in the window`() = strip(loops(20)) {
        onNodeWithTag(LoopChipsTags.SCROLLBAR).assertExists()
        val newLoop = onNodeWithTag(LoopChipsTags.NEW_LOOP).assertIsDisplayed().getBoundsInRoot()
        assertTrue(newLoop.right.value <= WIDTH, "pinned at the end, inside the window")
        assertTrue(onAllNodesWithText("Loop 20").fetchSemanticsNodes().isEmpty(), "the last chip is out of sight")
        onNodeWithTag(LoopChipsTags.NEW_LOOP).performClick()
        assertEquals(1, newLoops)
    }

    @Test
    fun `when the loops fit then there is no scrollbar and New loop follows the last chip`() = strip(loops(2)) {
        onNodeWithTag(LoopChipsTags.SCROLLBAR).assertDoesNotExist()
        val gap = onNodeWithTag(LoopChipsTags.NEW_LOOP).getBoundsInRoot().left.value - boundsOf("Loop 2").right
        assertEquals(8f, gap, 0.5f)
    }

    @Test
    fun `when the plain wheel turns over the strip then it scrolls sideways`() = strip(loops(20)) {
        val before = boundsOf("Loop 4").left
        onNodeWithTag(LoopChipsTags.STRIP).performMouseInput {
            moveTo(center)
            scroll(3f, ScrollWheel.Vertical)
        }
        waitForIdle()
        assertEquals(before - 3 * 64f, boundsOf("Loop 4").left, 0.5f, "wheel down scrolls towards the end, 64 dp a notch")

        onNodeWithTag(LoopChipsTags.STRIP).performMouseInput { scroll(-30f, ScrollWheel.Vertical) }
        waitForIdle()
        assertEquals(24f, boundsOf("Loop 1").left, 0.5f, "wheel up scrolls back to the start")
    }

    @Test
    fun `when the wheel turns sideways over the strip then it scrolls too`() = strip(loops(20)) {
        val before = boundsOf("Loop 4").left
        onNodeWithTag(LoopChipsTags.STRIP).performMouseInput {
            moveTo(center)
            scroll(3f, ScrollWheel.Horizontal)
        }
        waitForIdle()
        // How far is the platform's own step (Windows: a share of the width, macOS: 10 dp a unit).
        assertTrue(boundsOf("Loop 4").left < before - 10, "${boundsOf("Loop 4")}")
    }

    @Test
    fun `when the loops fit then the wheel is left to the window`() {
        val page = mutableStateOf(0)
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                SlowDownerTheme {
                    val scroll = rememberScrollState()
                    page.value = scroll.value
                    Column(Modifier.verticalScroll(scroll)) {
                        LoopChipsStrip(loops(2), emptyList(), actions)
                        Spacer(Modifier.height(1_000.dp))
                    }
                }
            }
            val before = boundsOf("Loop 1").left
            onNodeWithTag(LoopChipsTags.STRIP).performMouseInput {
                moveTo(center)
                scroll(3f, ScrollWheel.Vertical)
            }
            waitForIdle()
            assertTrue(page.value > 0, "the page under the strip scrolled")
            assertEquals(before, boundsOf("Loop 1").left, 0.5f, "the chips did not move")
        }
    }

    @Test
    fun `when the active loop changes to a hidden chip then it is scrolled fully into view`() {
        var state by mutableStateOf(loops(20, active = 1))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { SlowDownerTheme { LoopChipsStrip(state, emptyList(), actions) } }
            state = loops(20, active = 17)
            waitForIdle()
            assertFullyInList(boundsOf("Loop 17"))

            state = loops(20, active = 3)
            waitForIdle()
            assertFullyInList(boundsOf("Loop 3"))
        }
    }

    @Test
    fun `when a half hidden chip is double clicked then it is revealed and renamed`() {
        var state by mutableStateOf(loops(20, active = 1))
        val selecting = LoopChipActions({ id -> state = loops(20, active = id.toInt()) }, {}, { _, _ -> }, {}, {}, {})
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { SlowDownerTheme { LoopChipsStrip(state, emptyList(), selecting) } }
            val list = onNodeWithTag(LoopChipsTags.LIST).fetchBounds()
            val half = (1..20).map { "Loop $it" }.first { boundsOf(it).right >= list.right - 1f }
            onNodeWithText(half).performMouseInput { doubleClick(Offset(10f, height / 2f)) }
            waitForIdle()
            onNodeWithContentDescription("Rename loop").assertExists()
            assertFullyInList(onNodeWithContentDescription("Rename loop").fetchBounds())
        }
    }

    @Test
    fun `when only the passes change then the strip stays where the user scrolled it`() {
        var state by mutableStateOf(loops(20, active = 1))
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent { SlowDownerTheme { LoopChipsStrip(state, emptyList(), actions) } }
            onNodeWithTag(LoopChipsTags.STRIP).performMouseInput {
                moveTo(center)
                scroll(6f, ScrollWheel.Vertical)
            }
            waitForIdle()
            val shown = firstShown()
            val scrolled = boundsOf(shown).left
            state = state.copy(chips = state.chips.map { if (it.isActive) it.copy(passes = 3) else it })
            waitForIdle()
            assertEquals(scrolled, boundsOf(shown).left, 0.5f)
        }
    }

    @Test
    fun `when the keyboard focus moves to a hidden chip then it comes into view`() = strip(loops(20)) {
        onNodeWithText("Loop 1").performSemanticsAction(SemanticsActions.RequestFocus)
        repeat(14) { onRoot().performKeyInput { pressKey(Key.Tab) } }
        waitForIdle()
        val chip = boundsOf("Loop 15")
        assertTrue(chip.left >= 0f && chip.right <= onNodeWithTag(LoopChipsTags.LIST).fetchBounds().right, "$chip")
    }

    private fun strip(state: LoopChipsState, block: DesktopComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            setContent {
                SlowDownerTheme {
                    Box(Modifier.width(WIDTH.dp)) { LoopChipsStrip(state, emptyList(), actions) }
                }
            }
            block()
        }

    /** The first chip on screen, by name. */
    private fun DesktopComposeUiTest.firstShown(): String =
        (1..20).map { "Loop $it" }.first { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() && boundsOf(it).left >= 0f }

    private fun DesktopComposeUiTest.boundsOf(name: String): Rect =
        onNodeWithText(name).fetchBounds()

    private fun SemanticsNodeInteraction.fetchBounds(): Rect {
        val bounds = getBoundsInRoot()
        return Rect(bounds.left.value, bounds.top.value, bounds.right.value, bounds.bottom.value)
    }

    /** Whole, inside the scrolling part and clear of its edge fades. */
    private fun DesktopComposeUiTest.assertFullyInList(chip: Rect) {
        val list = onNodeWithTag(LoopChipsTags.LIST).fetchBounds()
        assertTrue(chip.left >= list.left + FADE - 0.5f, "$chip starts inside $list")
        assertTrue(chip.right <= list.right - FADE + 0.5f, "$chip ends inside $list")
    }

    private fun loops(count: Int, active: Int = 1) = LoopChipsState(
        chips = (1..count).map {
            LoopChipModel(id = it.toLong(), name = "Loop $it", colorIndex = (it - 1) % 4, lengthSeconds = 8f, passes = 0, isActive = it == active)
        },
        enabled = true,
    )

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 120
        const val FADE = 32f
    }
}
