package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.domain.library.ImportDocumentUseCase
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.Koin
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.assertIs

/**
 * The real app (desktop bindings) in an off-screen scene of [widthDp] × [heightDp], with a 30 s track
 * open. Every call runs on the Swing thread, where the ViewModels live, and lets Room settle between
 * steps, as each loop edit waits for it.
 */
class PlayerScene(
    private val koin: Koin,
    val widthDp: Int,
    val heightDp: Int,
    val scale: Int = 2,
    theme: ThemeMode = ThemeMode.Dark,
    private val textInput: TextInputFocus = TextInputFocus(),
) : AutoCloseable {

    private lateinit var scene: ImageComposeScene
    lateinit var player: PlayerViewModel
        private set
    private var time = 0L

    init {
        SwingUtilities.invokeAndWait {
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            val store = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
            scene = ImageComposeScene(width = widthDp * scale, height = heightDp * scale, density = Density(scale.toFloat())) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycle,
                    LocalViewModelStoreOwner provides store,
                    LocalTextInputFocus provides textInput,
                ) {
                    SlowDownerTheme(theme) {
                        val viewModels = ActivityViewModels(koinViewModel(), koinViewModel(), koinViewModel(), koinViewModel())
                        player = viewModels.player
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) { SlowDownerApp(viewModels, noActions()) }
                        }
                    }
                }
            }
            scene.render(0)
        }
    }

    /** Opens a 30 s track, sets 92 BPM and three loops (the second active, so Progression and Notes show). */
    fun openTrackWithLoops() {
        val audio = File(Files.createTempDirectory("downstep-player-audio").toFile(), "Easy Blues.wav")
        audio.writeBytes(musicWav())
        val imported = runBlocking { koin.get<ImportDocumentUseCase>()(audio.path) }
        assertIs<ImportResult.Success>(imported)
        steps(
            { it.onTrackSelected(imported.track) },
            { it.onBpmChanged(92f) },
            { it.onSeek(0.05f) },
            { it.onNewLoopAtPlayhead() },
            { it.onSeek(0.35f) },
            { it.onNewLoopAtPlayhead() },
            { it.onSeek(0.7f) },
            { it.onNewLoopAtPlayhead() },
            { it.onPreviousLoopClicked() },
            { it.onSeek(0.42f) },
        )
    }

    /** Runs each step on the Swing thread, a few frames apart. */
    fun steps(vararg steps: (PlayerViewModel) -> Unit) {
        steps.forEach { step ->
            SwingUtilities.invokeAndWait { step(player) }
            frames(4, 80)
        }
    }

    fun frames(count: Int, millis: Long = 16) = repeat(count) {
        Thread.sleep(millis)
        time += millis * 1_000_000L
        SwingUtilities.invokeAndWait { scene.render(time) }
    }

    /** Waits for the last edits, then renders the frame, saved as [output] when given. */
    fun capture(output: File? = null): Image {
        frames(6, 120)
        lateinit var image: Image
        SwingUtilities.invokeAndWait {
            image = scene.render(time + 2_000_000_000L)
            output?.let {
                it.parentFile.mkdirs()
                it.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            }
        }
        return image
    }

    /** Every semantics node on screen, unmerged. */
    fun nodes(): List<SemanticsNode> {
        lateinit var nodes: List<SemanticsNode>
        SwingUtilities.invokeAndWait { nodes = scene.semanticsOwners.flatMap { it.unmergedRootSemanticsNode.descendants() } }
        return nodes
    }

    /** The bounds in dp of the node tagged [tag]; null when it is not on screen. */
    fun boundsOf(tag: String): Rect? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        ?.boundsInRoot?.let { Rect(it.left / scale, it.top / scale, it.right / scale, it.bottom / scale) }

    /** How far the node tagged [tag] scrolls, in px; 0 when everything shows. */
    fun scrollRangeOf(tag: String): Float? = nodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        ?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke()

    /** The centre in px of the first node whose text is [text]. */
    fun centreOfText(text: String): androidx.compose.ui.geometry.Offset? = nodes()
        .firstOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
        ?.boundsInRoot?.center

    fun send(block: ImageComposeScene.() -> Unit) {
        SwingUtilities.invokeAndWait { scene.block() }
        frames(2)
    }

    override fun close() {
        SwingUtilities.invokeAndWait { scene.close() }
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun noActions() = ActivityActions(
        onPickFile = {}, onOpenSpotify = null, onPlayStop = {}, onPlaybackStarted = {},
        onShareExport = {}, onSaveExport = {}, onPickBackup = {}, onShareBackup = {}, onSaveBackup = {},
    )

    companion object {
        /** 30 s of a chord whose loudness swells and fades, so the waveform has a shape. */
        fun musicWav(): ByteArray {
            val rate = 44_100
            val frames = 30 * rate
            val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { i ->
                val t = i.toDouble() / rate
                val envelope = 0.25 + 0.75 * abs(sin(2 * PI * t / 7.0)) * (0.6 + 0.4 * abs(sin(2 * PI * t * 1.5)))
                val chord = sin(2 * PI * 220 * t) + 0.6 * sin(2 * PI * 277.2 * t) + 0.4 * sin(2 * PI * 329.6 * t)
                val sample = (chord / 2.0 * envelope * 26_000).toInt().coerceIn(-32_767, 32_767).toShort()
                data.putShort(sample).putShort(sample)
            }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(2); putInt(rate); putInt(rate * 4); putShort(4); putShort(16)
                put("data".toByteArray()); putInt(frames * 4)
            }
            return header.array() + data.array()
        }
    }
}
