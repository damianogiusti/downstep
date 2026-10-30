package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.data.support.SupportPromptDto
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.ui.support.SupportPromptHost
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.whatsnew.WhatsNewHost
import org.jetbrains.skia.EncodedImageFormat
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The What's new popup over the app's own bindings, in both themes, saved in build/screenshots. The
 * support prompt is due too (third launch): only What's new may show.
 */
class WhatsNewRenderTest {

    private val directories = AppDirectories(Files.createTempDirectory("slowdowner-whatsnew").toFile())
    private val changelogDir = Files.createTempDirectory("slowdowner-changelog").toFile().apply {
        File(this, "CHANGELOG.txt").writeText(
            """
            # Changelog

            ## 1.3.0

            - Loops remember where you left them.
            - Bug fixes and small improvements.

            ## 1.2.0

            - Normalize volume: every song plays at a similar level.
            - If you open Downstep while it is already running, the open window comes to the front.
            """.trimIndent(),
        )
    }
    private val whatsNewStore = InMemoryWhatsNewStore().apply { stored = "1.1.0" }
    private val koin = startKoin {
        modules(
            desktopModule(
                directories, NativeEngine.manual(44_100), InMemoryEffectsSettingsStore(),
                InMemorySupportPromptStore().apply { stored = SupportPromptDto(launchCount = 3, lastShownAtMillis = null) },
                whatsNew = WhatsNewSettings("1.3.0", hasLaunchHistory = true, whatsNewStore, changelogDir),
            ),
            useCasesModule,
            uiModule,
        )
    }.koin

    @AfterTest
    fun tearDown() {
        koin.get<DesktopPlaybackEngine>().close()
        stopKoin()
        directories.root.deleteRecursively()
        changelogDir.deleteRecursively()
    }

    @Test
    fun `the popup shows over the app in the light theme`() = assertRenders(darkTheme = false, File("build/screenshots/whats-new-light.png"))

    @Test
    fun `the popup shows over the app in the dark theme`() = assertRenders(darkTheme = true, File("build/screenshots/whats-new-dark.png"))

    private fun assertRenders(darkTheme: Boolean, output: File) {
        output.parentFile.mkdirs()
        lateinit var scene: ImageComposeScene
        SwingUtilities.invokeAndWait {
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            scene = ImageComposeScene(width = 1280 * 2, height = 820 * 2, density = Density(2f)) {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle, LocalViewModelStoreOwner provides store) {
                    SlowDownerTheme(darkTheme = darkTheme) {
                        Box(modifier = Modifier.fillMaxSize())
                        WhatsNewHost()
                        SupportPromptHost()
                    }
                }
            }
            scene.render(0)
        }
        repeat(10) { frame ->
            Thread.sleep(150)
            SwingUtilities.invokeAndWait { scene.render((frame + 1) * 150_000_000L) }
        }
        var bytes = 0
        SwingUtilities.invokeAndWait {
            val png = requireNotNull(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)).bytes
            output.writeBytes(png)
            bytes = png.size
            scene.close()
        }
        assertTrue(bytes > 10_000, "suspiciously small render: $bytes bytes")
        // Shown once: the running version is on record now.
        assertEquals("1.3.0", whatsNewStore.stored)
    }
}
