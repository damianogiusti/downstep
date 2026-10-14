package com.damianogiusti.slowdowner.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.library.ImportViewModel
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.error_engine_unavailable
import com.damianogiusti.slowdowner.ui.resources.error_no_audio_output
import com.damianogiusti.slowdowner.ui.resources.menu_close_window
import com.damianogiusti.slowdowner.ui.resources.menu_file
import com.damianogiusti.slowdowner.ui.resources.menu_minimize
import com.damianogiusti.slowdowner.ui.resources.menu_window
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import java.awt.Dimension
import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
    val directories = AppDirectories()
    AppLog.start(directories.logs, appVersion)
    CrashReporting.start(appVersion, directories)
    val logger = CrashReporting.logger()
    val (engine, hasAudioOutput) = startEngine(logger)
    // One line per run: how many people use which OS, and whether the engine started there.
    logger.i(
        "App started",
        mapOf(
            "engine" to if (engine != null) "ok" else "unavailable",
            "audioOutput" to hasAudioOutput.toString(),
            "java" to System.getProperty("java.version").orEmpty(),
        ),
    )
    if (engine != null) {
        val koin = startKoin { modules(desktopModule(directories, engine, logger = logger), useCasesModule, uiModule) }.koin
        // Main (Swing) thread, as on Android: engine settings are applied from the UI thread.
        startAppWork(koin, MainScope())
    }
    // Set once the ViewModels exist; read by the window's key handler.
    var shortcuts: PracticeShortcuts? = null
    application(exitProcessOnExit = false) {
        // Wide enough for the library and the player side by side (see SlowDownerApp).
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = ::exitApplication,
            // Shown on Windows: tells which build the band is trying. Hidden on macOS (WindowChrome).
            title = "Downstep $appVersion",
            state = windowState,
            onPreviewKeyEvent = { event -> shortcuts?.handle(event) ?: false },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                window.useTransparentTitleBar()
            }
            // macOS only: there the menus sit in the screen's menu bar. On Windows they would be a bar
            // inside the window, and Alt+F4 already closes it. One window: closing it quits.
            if (isMacOs) MenuBar {
                Menu(stringResource(Res.string.menu_file)) {
                    Item(stringResource(Res.string.menu_close_window), shortcut = KeyShortcut(Key.W, meta = true), onClick = ::exitApplication)
                }
                Menu(stringResource(Res.string.menu_window)) {
                    Item(stringResource(Res.string.menu_minimize), shortcut = KeyShortcut(Key.M, meta = true)) { windowState.isMinimized = true }
                }
            }
            SlowDownerTheme {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = titleBarHeight)) {
                    if (engine != null && !hasAudioOutput) NoAudioOutputBanner()
                    Box(modifier = Modifier.weight(1f)) {
                        if (engine == null) EngineUnavailable() else DesktopApp(window, logger) { shortcuts = it }
                    }
                }
            }
        }
    }
    // Only now no UI can call the engine any more: stop polling and free the native side.
    if (engine != null) KoinPlatform.getKoin().get<DesktopPlaybackEngine>().close()
    CrashReporting.stop()
    exitProcess(0)
}

/** Set by Gradle (-PappVersion, see desktopApp/build.gradle.kts). */
private val appVersion: String = System.getProperty("slowdowner.version") ?: "dev"

// In points, like Compose's dp on macOS. Narrower than this the two panes get cramped.
private const val MIN_WIDTH = 900
private const val MIN_HEIGHT = 600

/** A null engine shows the error screen: no library, or a library that does not load. */
private data class EngineStart(val engine: NativeEngine?, val hasAudioOutput: Boolean)

private fun startEngine(logger: Logger): EngineStart = runCatching {
    val library = engineLibrary() ?: error("libsdengine not found")
    NativeEngine.withDevice(library)?.let { EngineStart(it, hasAudioOutput = true) }
        ?: run {
            // No output device (a PC without speakers, a CI machine): library, imports and exports
            // still work. Playback stays silent and does not move: a banner says so.
            logger.w("No audio output device: playback is off")
            EngineStart(NativeEngine.manual(NO_OUTPUT_SAMPLE_RATE, library), hasAudioOutput = false)
        }
}
    // The app is useless without it: the most important line of all.
    .onFailure { logger.e("Cannot start the audio engine", it) }
    .getOrElse { EngineStart(null, hasAudioOutput = false) }

private const val NO_OUTPUT_SAMPLE_RATE = 44_100

@Composable
private fun NoAudioOutputBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.error_no_audio_output),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DesktopApp(window: java.awt.Frame, logger: Logger, onShortcutsReady: (PracticeShortcuts) -> Unit) {
    // One store for the window, as the activity is on Android: pickers report into these ViewModels.
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    val textInput = remember { TextInputFocus() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalTextInputFocus provides textInput) {
        val viewModels = ActivityViewModels(
            player = koinViewModel<PlayerViewModel>(),
            imports = koinViewModel<ImportViewModel>(),
            backup = koinViewModel<BackupViewModel>(),
        )
        val actions = remember(viewModels) { DesktopActions(window, viewModels, logger).toActivityActions() }
        LaunchedEffect(viewModels.player) { onShortcutsReady(PracticeShortcuts(viewModels.player, textInput)) }
        SlowDownerApp(viewModels = viewModels, activity = actions)
    }
}

@Composable
private fun EngineUnavailable() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.error_engine_unavailable),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** Next to the app resources when packaged or run from Gradle; overridable for development. */
private fun engineLibrary(): Path? {
    System.getProperty(NativeEngine.LIBRARY_PROPERTY)?.let { return Path.of(it) }
    val resources = System.getProperty("compose.application.resources.dir") ?: return null
    return Path.of(resources, System.mapLibraryName("sdengine")).takeIf { it.toFile().exists() }
}
