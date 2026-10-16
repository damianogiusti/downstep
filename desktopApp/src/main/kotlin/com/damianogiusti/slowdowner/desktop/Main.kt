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
import com.damianogiusti.slowdowner.desktop.update.UpdateSettings
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
import com.damianogiusti.slowdowner.ui.resources.menu_help
import com.damianogiusti.slowdowner.ui.resources.menu_minimize
import com.damianogiusti.slowdowner.ui.resources.menu_window
import com.damianogiusti.slowdowner.ui.resources.support_donate
import com.damianogiusti.slowdowner.ui.support.SupportPromptHost
import com.damianogiusti.slowdowner.ui.update.UpdateBanner
import com.damianogiusti.slowdowner.ui.update.UpdateHost
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import com.damianogiusti.slowdowner.ui.theme.ThemeViewModel
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
    val directories = AppDirectories()
    AppLog.start(directories.logs, appVersion)
    CrashReporting.start(appVersion, directories)
    val logger = CrashReporting.logger()
    val (engine, hasAudioOutput) = startEngine(logger)
    val updates = UpdateSettings.current(appVersion)
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
        val koin = startKoin { modules(desktopModule(directories, engine, logger = logger, updates = updates), useCasesModule, uiModule) }.koin
        // Main (Swing) thread, as on Android: engine settings are applied from the UI thread.
        startAppWork(koin, MainScope())
    }
    // Set once the ViewModels exist; read by the window's key handler.
    var shortcuts: PracticeShortcuts? = null
    application(exitProcessOnExit = false) {
        // The size the Downstep 2.0 design is drawn at.
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = ::exitApplication,
            // Shown on Windows: tells which build the band is trying. Hidden on macOS (WindowChrome).
            title = "Downstep $appVersion",
            // From the first frame, so the coffee cup never shows; sharper sizes follow below.
            icon = remember { runCatching { appIconPainter() }.getOrNull() },
            state = windowState,
            onPreviewKeyEvent = { event -> shortcuts?.handle(event) ?: false },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WIDTH, MIN_HEIGHT)
                window.useTransparentTitleBar()
                window.useAppIcon()
                // A key held while switching away is released in the other app: forget it.
                window.addWindowFocusListener(object : WindowAdapter() {
                    override fun windowLostFocus(e: WindowEvent) {
                        shortcuts?.onFocusLost()
                    }
                })
                // The cold start as the user sees it: what class-data sharing and a lazy start shorten.
                sinceLaunchMillis()?.let { println("Window composed $it ms after launch") }
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
                // Where Mac users look for things about the app itself. Windows has the library menu only.
                Menu(stringResource(Res.string.menu_help)) {
                    Item(stringResource(Res.string.support_donate)) { openDonation(DonateSource.MAC_MENU, logger) }
                }
            }
            // One store for the window, as the activity is on Android: pickers report into these ViewModels,
            // and the player, its effects and the theme live as long as the window.
            val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                // Without an engine there is no Koin: the error screen gets the default theme.
                val themeMode = if (engine != null) koinViewModel<ThemeViewModel>().mode.collectAsStateWithLifecycle().value else ThemeMode.DEFAULT
                SlowDownerTheme(themeMode) {
                    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = titleBarHeight)) {
                        if (engine != null && !hasAudioOutput) NoAudioOutputBanner()
                        Box(modifier = Modifier.weight(1f)) {
                            if (engine == null) {
                                EngineUnavailable()
                            } else {
                                DesktopApp(window, logger, onRestart = ::exitApplication, installWithoutAsking = updates.installWithoutAsking) {
                                    shortcuts = it
                                }
                            }
                        }
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

/** Null where the OS does not tell when the process started. */
private fun sinceLaunchMillis(): Long? = ProcessHandle.current().info().startInstant().map {
    java.time.Duration.between(it, java.time.Instant.now()).toMillis()
}.orElse(null)

/** Set by Gradle (-PappVersion, see desktopApp/build.gradle.kts). */
private val appVersion: String = System.getProperty("slowdowner.version") ?: "dev"

// In points, like Compose's dp on macOS. The player's card rows need this much (README §6: nothing
// narrower is designed).
private const val MIN_WIDTH = 1000
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

/** [onRestart] quits the app for an update: the new version opens by itself. */
@Composable
private fun DesktopApp(
    window: java.awt.Frame,
    logger: Logger,
    onRestart: () -> Unit,
    installWithoutAsking: Boolean,
    onShortcutsReady: (PracticeShortcuts) -> Unit,
) {
    val textInput = remember { TextInputFocus() }
    CompositionLocalProvider(LocalTextInputFocus provides textInput) {
        val player = koinViewModel<PlayerViewModel>()
        val imports = koinViewModel<ImportViewModel>()
        val backup = koinViewModel<BackupViewModel>()
        // One instance for the window's life, so SlowDownerApp's arguments stay equal between recompositions.
        val viewModels = remember(player, imports, backup) { ActivityViewModels(player, imports, backup) }
        val actions = remember(viewModels) { DesktopActions(window, viewModels, logger).toActivityActions() }
        LaunchedEffect(viewModels.player) { onShortcutsReady(PracticeShortcuts(viewModels.player, textInput)) }
        Column(modifier = Modifier.fillMaxSize()) {
            UpdateBanner()
            Box(modifier = Modifier.weight(1f)) { SlowDownerApp(viewModels = viewModels, activity = actions) }
        }
        SupportPromptHost()
        // After the support prompt: its dialog waits until that one is closed.
        UpdateHost(onRestart = onRestart, installWithoutAsking = installWithoutAsking)
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
