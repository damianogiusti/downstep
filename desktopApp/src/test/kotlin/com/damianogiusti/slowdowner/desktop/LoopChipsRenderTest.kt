package com.damianogiusti.slowdowner.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.player.components.LoopChipsTags
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The real player with 20 loops, more than the strip shows: build/screenshots/loop-chips-start.png
 * (the first loop active), -scrolled.png (after three wheel notches over the strip) and -revealed.png
 * (the last loop made active: scrolled to). "+ New loop" stays in the window throughout.
 */
class LoopChipsRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("downstep-chips-render").toFile())
    private val koin = startKoin {
        modules(
            desktopModule(directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(), InMemorySupportPromptStore(),
                clickSettingsStore = InMemoryClickSettingsStore()),
            useCasesModule,
            uiModule,
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
    }

    @Test
    fun `twenty loops scroll in the strip and New loop stays in the window`() {
        PlayerScene(koin, 1280, 720).use { scene ->
            scene.openTrackWithTwentyLoops()
            // From the last loop, ] goes round to the first: the strip scrolls back to the start.
            scene.steps({ it.onNextLoopClicked() })
            scene.capture(File("build/screenshots/loop-chips-start.png"))
            assertNotNull(scene.boundsOf(LoopChipsTags.SCROLLBAR), "the strip overflows: its scrollbar shows")
            scene.assertNewLoopInWindow()
            assertTrue(scene.centreOfText("Loop 9") == null, "the ninth chip is beyond the strip")

            val strip = requireNotNull(scene.boundsOf(LoopChipsTags.LIST))
            scene.send {
                sendPointerEvent(PointerEventType.Scroll, strip.center * scene.scale.toFloat(), scrollDelta = Offset(0f, 3f))
            }
            scene.capture(File("build/screenshots/loop-chips-scrolled.png"))
            val ninth = requireNotNull(scene.centreOfText("Loop 9")) { "three notches bring the ninth chip in" }
            assertTrue(ninth.x / scene.scale < strip.right)
            scene.assertNewLoopInWindow()

            // [ from the first goes round to the last: scrolled fully into view.
            scene.steps({ it.onPreviousLoopClicked() })
            scene.capture(File("build/screenshots/loop-chips-revealed.png"))
            scene.assertNewLoopInWindow()
            val last = requireNotNull(scene.centreOfText("Loop 20")) { "the last chip is shown" }
            assertTrue(last.x / scene.scale < requireNotNull(scene.boundsOf(LoopChipsTags.LIST)).right)
        }
    }

    @Test
    fun `twenty loops in the light theme on a full screen`() {
        PlayerScene(koin, 1920, 1080, scale = 1, theme = ThemeMode.Light).use { scene ->
            scene.openTrackWithTwentyLoops()
            scene.capture(File("build/screenshots/loop-chips-light-1920.png"))
            assertNotNull(scene.boundsOf(LoopChipsTags.SCROLLBAR))
            scene.assertNewLoopInWindow()
            assertNotNull(scene.centreOfText("Loop 20"), "the new, active loop is in view")
        }
    }

    /** Three loops from [PlayerScene.openTrackWithLoops], then 17 more after them: the last is active. */
    private fun PlayerScene.openTrackWithTwentyLoops() {
        openTrackWithLoops()
        repeat(17) { i ->
            steps({ it.onSeek(0.72f + i * 0.015f) }, { it.onNewLoopAtPlayhead() })
        }
        steps({ it.onSeek(0.42f) })
    }

    private fun PlayerScene.assertNewLoopInWindow() {
        val newLoop = requireNotNull(boundsOf(LoopChipsTags.NEW_LOOP)) { "+ New loop is on screen" }
        assertTrue(newLoop.right <= widthDp, "+ New loop ends inside the window: $newLoop")
    }
}
