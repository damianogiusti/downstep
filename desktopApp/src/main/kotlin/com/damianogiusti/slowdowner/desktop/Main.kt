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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.data.support.PreferencesSupportPromptStore
import com.damianogiusti.slowdowner.desktop.update.DesktopUpdateInstaller
import com.damianogiusti.slowdowner.desktop.update.UpdateSettings
import com.damianogiusti.slowdowner.desktop.update.UpdateSwapFiles
import com.damianogiusti.slowdowner.desktop.update.UpdatingNotice
import com.damianogiusti.slowdowner.di.uiModule
import com.damianogiusti.slowdowner.di.useCasesModule
import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.ui.common.LocalTextInputFocus
import com.damianogiusti.slowdowner.ui.common.TextInputFocus
import com.damianogiusti.slowdowner.ui.effects.EffectsViewModel
import com.damianogiusti.slowdowner.ui.library.ImportViewModel
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import com.damianogiusti.slowdowner.ui.navigation.SlowDownerApp
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.contact_menu_mac
import com.damianogiusti.slowdowner.ui.resources.error_engine_unavailable
import com.damianogiusti.slowdowner.ui.resources.error_no_audio_output
import com.damianogiusti.slowdowner.ui.resources.menu_close_window
import com.damianogiusti.slowdowner.ui.resources.menu_file
import com.damianogiusti.slowdowner.ui.resources.menu_help
import com.damianogiusti.slowdowner.ui.resources.menu_minimize
import com.damianogiusti.slowdowner.ui.resources.menu_window
import com.damianogiusti.slowdowner.ui.resources.support_donate
import com.damianogiusti.slowdowner.ui.support.ContactDialog
import com.damianogiusti.slowdowner.ui.support.SupportPromptHost
import com.damianogiusti.slowdowner.ui.update.UpdateBanner
import com.damianogiusti.slowdowner.ui.update.UpdateHost
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.system.exitProcess

fun main() {
    val directories = AppDirectories()
    // An update is putting the new version in place, and this copy would lock its files: it opens again
    // by itself. Before anything else, so that this process ends as soon as it can.
    if (UpdateSwapFiles.isSwapRunning(directories.updateWork)) {
        // However the notice behaves, this process must not outlive it by much: it locks the app's files.
        thread(isDaemon = true) {
            Thread.sleep(NOTICE_DEADLINE_MS)
            Runtime.getRuntime().halt(0)
        }
        UpdatingNotice.show()
        exitProcess(0)
    }
    // First of all: a second copy must not rotate the first one's log or touch its crash marker.
    val instanceProblems = mutableListOf<Triple<String, Throwable?, Map<String, String>>>()
    val instance = SingleInstance.acquire(directories.root) { message, error, properties ->
        instanceProblems += Triple(message, error, properties)
    } ?: run {
        println("Downstep is already running: asked it to come to the front")
        exitProcess(0)
    }
    AppLog.start(directories.logs, appVersion)
    CrashReporting.start(appVersion, directories)
    val logger = CrashReporting.logger()
    instanceProblems.forEach { (message, error, properties) -> logger.w(message, error, properties) }
    // How the last update went on this computer: the script itself cannot report.
    UpdateSwapFiles.takeResult(directories.updateWork)?.let { logger.i("Update swap result", mapOf("outcome" to it)) }
    val (engine, hasAudioOutput) = startEngine(logger)
    val updates = UpdateSettings.current(appVersion)
    thread(isDaemon = true, name = "discard-update") { DesktopUpdateInstaller.discardAbandoned(updates.installation, directories.updateWork) }
    // Read before this run counts itself (RecordAppLaunchUseCase, in startAppWork).
    val hasLaunchHistory = runCatching { PreferencesSupportPromptStore().read() != null }.getOrDefault(false)
    // One line per run: how many people use which OS, and whether the engine started there.
    logger.i(
        "App started",
        mapOf(
            "engine" to if (engine != null) "ok" else "unavailable",
            "audioOutput" to hasAudioOutput.toString(),
            "java" to System.getProperty("java.version").orEmpty(),
            "arch" to cpuArchitecture(),
        ) + launchProperties(CrashReporting.isNewInstallation, hasLaunchHistory),
    )
    // Main (Swing) thread, as on Android: engine settings are applied from the UI thread.
    val appScope = MainScope()
    if (engine != null) {
        val koin = startKoin { modules(desktopModule(directories, engine, logger = logger, updates = updates), useCasesModule, uiModule) }.koin
        startAppWork(koin, appScope)
    }
    // One store for the window, as the activity is on Android: pickers report into these ViewModels.
    val viewModelOwner = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    // Set once the ViewModels exist; read by the window's key handler.
    var shortcuts: PracticeShortcuts? = null
    // Set once the ViewModels exist; run after the window is gone.
    var flushOnQuit: (suspend () -> Unit)? = null
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
                // Once per run: tells how slow a cold start is where people see the warning about a second copy.
                logger.i("Window shown", mapOf("startup" to startupBucket(secondsSinceProcessStart())))
            }
            DisposableEffect(Unit) {
                instance.onAnotherLaunch = {
                    val shown = CompletableFuture<Unit>()
                    SwingUtilities.invokeLater {
                        bringToFront(window, windowState)
                        shown.complete(Unit)
                    }
                    // A frozen window cannot come forward: then the new launch must not quit.
                    runCatching { shown.get(1, TimeUnit.SECONDS) }.isSuccess.also { cameForward ->
                        // Tells how often people start the app again while it runs.
                        if (cameForward) logger.i("Second launch")
                    }
                }
                // The window goes only when the app quits: from now on a new launch waits for this copy to exit.
                onDispose { instance.quitting() }
            }
            var contactSource by remember { mutableStateOf<ContactSource?>(null) }
            val contact = remember { ContactActions(logger, appVersion) }
            fun openContact(source: ContactSource) {
                contact.opened(source)
                contactSource = source
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
                    Item(stringResource(Res.string.contact_menu_mac)) { openContact(ContactSource.MAC_MENU) }
                }
            }
            SlowDownerTheme {
                Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = titleBarHeight)) {
                    if (engine != null && !hasAudioOutput) NoAudioOutputBanner()
                    Box(modifier = Modifier.weight(1f)) {
                        if (engine == null) {
                            EngineUnavailable()
                        } else {
                            DesktopApp(
                                window, viewModelOwner, logger, onRestart = ::exitApplication, installWithoutAsking = updates.installWithoutAsking,
                                onContact = { openContact(ContactSource.LIBRARY_MENU) },
                                onQuitFlushReady = { flushOnQuit = it },
                                onShortcutsReady = { shortcuts = it },
                            )
                        }
                    }
                }
                if (contactSource != null) {
                    ContactDialog(CONTACT_ADDRESS, contact::copyAddress, contact::writeMail) { contactSource = null }
                }
            }
        }
    }
    val quitStarted = TimeSource.Monotonic.markNow()
    QuitWatchdog.start(logger)
    QuitWatchdog.step = "save"
    // The last EQ edit may still wait for its pause: the process exits before that ends, and clearing
    // the ViewModels below does not wait for the save it starts.
    flushOnQuit?.let { flush ->
        runCatching { runBlocking(Dispatchers.IO) { withTimeoutOrNull(QUIT_FLUSH_TIMEOUT_MS) { flush() } } }
            .onFailure { logger.e("Cannot save the last edits on quit", it) }
    }
    // The window is gone, but its ViewModels and the app work still run on the Swing thread: a track
    // left playing kept reading the position and hit the freed engine. Stopping them there, then
    // freeing the engine there, means none of them is halfway through a call.
    QuitWatchdog.step = "ui"
    SwingUtilities.invokeAndWait {
        viewModelOwner.viewModelStore.clear()
        appScope.cancel()
        QuitWatchdog.step = "engine"
        if (engine != null) KoinPlatform.getKoin().get<DesktopPlaybackEngine>().close()
    }
    // Before stop(): it flushes, so the line leaves with the rest. Over 10 s the watchdog halts instead.
    if (quitStarted.elapsedNow() > SLOW_QUIT) logger.i("Slow quit", mapOf("seconds" to "3-10"))
    QuitWatchdog.step = "crash-reporting"
    CrashReporting.stop()
    QuitWatchdog.step = "instance"
    instance.close()
    exitProcess(0)
}

private val SLOW_QUIT = 3.seconds

/**
 * A copy whose window is gone but whose process stays keeps the instance lock, and every new launch
 * waits for it. If the quit does not finish in time, halt: it skips shutdown hooks on purpose, as a
 * stuck hook is one of the things that can hold the process.
 */
private object QuitWatchdog {
    @Volatile
    var step = "save"

    fun start(logger: Logger, timeout: kotlin.time.Duration = 10.seconds) {
        thread(isDaemon = true, name = "quit-watchdog") {
            Thread.sleep(timeout.inWholeMilliseconds)
            runCatching {
                logger.w("Quit is stuck", properties = mapOf("step" to step))
                CrashReporting.flush(2.seconds)
            }
            Runtime.getRuntime().halt(0)
        }
    }
}

private fun secondsSinceProcessStart(): Long {
    val start = ProcessHandle.current().info().startInstant().orElse(null) ?: return 0
    return Duration.between(start, Instant.now()).seconds.coerceAtLeast(0)
}

/** Few values on purpose: they become a property in Sentry. */
internal fun startupBucket(seconds: Long): String = when {
    seconds < 3 -> "<3s"
    seconds < 10 -> "3-10s"
    seconds <= 20 -> "10-20s"
    else -> ">20s"
}

// Windows lets a process in the background only flash its taskbar button: staying on top for a
// moment brings the window up for real.
private fun bringToFront(window: java.awt.Window, state: WindowState) {
    state.isMinimized = false
    window.isAlwaysOnTop = true
    window.toFront()
    window.isAlwaysOnTop = false
    window.requestFocus()
}

/** Set by Gradle (-PappVersion, see desktopApp/build.gradle.kts). */
private val appVersion: String = System.getProperty("slowdowner.version") ?: "dev"

// The notice of a copy opened during an update shows for 3 s; past this the process ends anyway.
private const val NOTICE_DEADLINE_MS = 10_000L

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

/** Quitting waits this long at most for the last write (preferences). */
private const val QUIT_FLUSH_TIMEOUT_MS = 2_000L

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
    owner: ViewModelStoreOwner,
    logger: Logger,
    onRestart: () -> Unit,
    installWithoutAsking: Boolean,
    onContact: () -> Unit,
    onQuitFlushReady: (suspend () -> Unit) -> Unit,
    onShortcutsReady: (PracticeShortcuts) -> Unit,
) {
    val textInput = remember { TextInputFocus() }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, LocalTextInputFocus provides textInput) {
        val viewModels = ActivityViewModels(
            player = koinViewModel<PlayerViewModel>(),
            imports = koinViewModel<ImportViewModel>(),
            backup = koinViewModel<BackupViewModel>(),
            effects = koinViewModel<EffectsViewModel>(),
        )
        val actions = remember(viewModels) { DesktopActions(window, viewModels, logger, onContact).toActivityActions() }
        LaunchedEffect(viewModels.player) { onShortcutsReady(PracticeShortcuts(viewModels.player, textInput)) }
        LaunchedEffect(viewModels.effects) { onQuitFlushReady { viewModels.effects.flushPendingSave() } }
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

/** Few values on purpose: they are log properties. */
private fun cpuArchitecture(): String = when (System.getProperty("os.arch").orEmpty().lowercase()) {
    "aarch64", "arm64" -> "arm64"
    "x86_64", "amd64" -> "x64"
    else -> "other"
}
