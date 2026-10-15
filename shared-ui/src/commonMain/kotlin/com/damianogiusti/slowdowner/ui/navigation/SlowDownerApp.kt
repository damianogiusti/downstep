package com.damianogiusti.slowdowner.ui.navigation

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.ui.backup.BackupBanner
import com.damianogiusti.slowdowner.ui.backup.BackupReadyDialog
import com.damianogiusti.slowdowner.ui.backup.BackupUiState
import com.damianogiusti.slowdowner.ui.backup.BackupViewModel
import com.damianogiusti.slowdowner.ui.backup.PlaylistConflictDialog
import com.damianogiusti.slowdowner.ui.common.CollectMessages
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.library.ImportBanner
import com.damianogiusti.slowdowner.ui.library.ImportViewModel
import com.damianogiusti.slowdowner.ui.library.LibraryNavigation
import com.damianogiusti.slowdowner.ui.library.LibraryScreen
import com.damianogiusti.slowdowner.ui.player.ExportUiState
import com.damianogiusti.slowdowner.ui.player.MiniPlayerBar
import com.damianogiusti.slowdowner.ui.player.PlayerNavigation
import com.damianogiusti.slowdowner.ui.player.PlayerScreen
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.player.components.ExportReadyDialog
import com.damianogiusti.slowdowner.ui.playlists.AddToPlaylistSheet
import com.damianogiusti.slowdowner.ui.playlists.PlaylistNavigation
import com.damianogiusti.slowdowner.ui.playlists.PlaylistScreen

/** What the app needs from the activity: pickers, share sheet, service, permissions. */
class ActivityActions(
    val onPickFile: () -> Unit,
    /** Null while Spotify is disabled: its import option is then hidden. */
    val onOpenSpotify: (() -> Unit)?,
    /** Wraps play/stop to ask for the notification permission first. */
    val onPlayStop: () -> Unit,
    val onPlaybackStarted: () -> Unit,
    val onShareExport: (ExportedFile) -> Unit,
    val onSaveExport: (ExportedFile) -> Unit,
    val onPickBackup: () -> Unit,
    val onShareBackup: (BackupFile) -> Unit,
    val onSaveBackup: (BackupFile) -> Unit,
    /** Null where the app is not published (Android): the Donate menu item is then hidden. */
    val onDonate: (() -> Unit)? = null,
)

/** ViewModels scoped to the activity: results of activity pickers and intents land in them. */
class ActivityViewModels(
    val player: PlayerViewModel,
    val imports: ImportViewModel,
    val backup: BackupViewModel,
)

/**
 * Root of the UI: navigation between library, playlists and player, with the mini player,
 * the import progress and the snackbars shared by every screen.
 */
@Composable
fun SlowDownerApp(viewModels: ActivityViewModels, activity: ActivityActions) {
    val playerViewModel = viewModels.player
    val importViewModel = viewModels.imports
    val backupViewModel = viewModels.backup
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val import by importViewModel.state.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val isOnPlayer = backStackEntry?.destination?.hasRoute<PlayerRoute>() == true
    var trackForPlaylist by remember { mutableStateOf<LibraryTrack?>(null) }

    CollectMessages(playerViewModel.messages, snackbarHostState)
    CollectMessages(importViewModel.messages, snackbarHostState)
    CollectMessages(backupViewModel.messages, snackbarHostState)

    val isPlaying = player.controls.isPlaying
    LaunchedEffect(isPlaying) { if (isPlaying) activity.onPlaybackStarted() }

    // Phones (and narrow windows) get one screen at a time; wide windows get the library and the
    // player side by side, with the player always visible.
    BoxWithConstraints {
        val windowWidth = maxWidth
        val isWide = windowWidth >= WIDE_LAYOUT_MIN_WIDTH
        val openTrack: (LibraryTrack) -> Unit = { track ->
            playerViewModel.onTrackSelected(track)
            if (!isWide && !isOnPlayer) navController.navigate(PlayerRoute) { launchSingleTop = true }
        }
        val currentOpenTrack by rememberUpdatedState(openTrack)
        LaunchedEffect(importViewModel) { importViewModel.importedTracks.collect { currentOpenTrack(it) } }
        // The window got wide while the player page was open: the pane takes over.
        LaunchedEffect(isWide, isOnPlayer) { if (isWide && isOnPlayer) navController.popBackStack() }

        Scaffold(
            // Screens handle their own insets; this one only stacks the bottom bar.
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val track = player.track
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp),
                ) {
                    BackupBanner(backup, onCancelExport = backupViewModel::onExportCancelClicked)
                    ImportBanner(import)
                    AnimatedVisibility(
                        visible = track != null && player.controls.isOpen && !isOnPlayer && !isWide,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        if (track != null) {
                            MiniPlayerBar(
                                track = track,
                                controls = player.controls,
                                onOpen = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                                onPlayStop = activity.onPlayStop,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val screens = @Composable { modifier: Modifier ->
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                    modifier = modifier,
                ) {
                    composable<LibraryRoute> {
                        LibraryScreen(
                            navigation = LibraryNavigation(
                                onTrackClick = openTrack,
                                onPlaylistClick = { navController.navigate(PlaylistRoute(it.id)) },
                                onAddToPlaylist = { trackForPlaylist = it },
                                onImportFile = activity.onPickFile,
                                onImportSpotify = activity.onOpenSpotify,
                                onImportLink = importViewModel::onTextShared,
                                onRestoreBackup = activity.onPickBackup,
                                onExportTrackBackup = backupViewModel::onExportTrack,
                                onExportLibraryBackup = backupViewModel::onExportLibrary,
                                onDonate = activity.onDonate,
                            ),
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    composable<PlaylistRoute> {
                        PlaylistScreen(
                            navigation = PlaylistNavigation(
                                onBack = { navController.popBackStack() },
                                onTrackClick = openTrack,
                                onExportBackup = backupViewModel::onExportPlaylist,
                                onExportTrackBackup = backupViewModel::onExportTrack,
                            ),
                            snackbarHostState = snackbarHostState,
                        )
                    }
                    composable<PlayerRoute>(
                        enterTransition = { slideInVertically(tween(PLAYER_TRANSITION_MS)) { it } },
                        exitTransition = { fadeOut(tween(PLAYER_TRANSITION_MS)) },
                        popEnterTransition = { fadeIn(tween(PLAYER_TRANSITION_MS)) },
                        popExitTransition = { slideOutVertically(tween(PLAYER_TRANSITION_MS)) { it } },
                    ) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            navigation = PlayerNavigation(
                                onCollapse = { navController.popBackStack() },
                                onPlayStop = activity.onPlayStop,
                                onAddToPlaylist = { trackForPlaylist = it },
                            ),
                        )
                    }
            }
            }
            val contentModifier = Modifier.padding(padding).consumeWindowInsets(padding)
            if (isWide) {
                Row(modifier = contentModifier) {
                    screens(Modifier.weight(1f))
                    VerticalDivider()
                    Box(modifier = Modifier.width((windowWidth * PLAYER_PANE_FRACTION).coerceIn(PLAYER_PANE_MIN, PLAYER_PANE_MAX))) {
                        PlayerScreen(
                            viewModel = playerViewModel,
                            navigation = PlayerNavigation(
                                onCollapse = null,
                                onPlayStop = activity.onPlayStop,
                                onAddToPlaylist = { trackForPlaylist = it },
                            ),
                        )
                    }
                }
            } else {
                screens(contentModifier)
            }
        }
    }

    trackForPlaylist?.let { track ->
        AddToPlaylistSheet(track = track, snackbarHostState = snackbarHostState, onDismiss = { trackForPlaylist = null })
    }
    (player.export as? ExportUiState.Ready)?.let { ready ->
        ExportReadyDialog(
            file = ready.file,
            onShare = {
                activity.onShareExport(ready.file)
                playerViewModel.onExportDismissed()
            },
            // The dialog stays until a target is picked: see PlayerViewModel.onExportSaveTargetPicked.
            onSave = { activity.onSaveExport(ready.file) },
            onDismiss = playerViewModel::onExportDismissed,
        )
    }
    (backup as? BackupUiState.Ready)?.let { ready ->
        BackupReadyDialog(
            file = ready.file,
            onShare = {
                activity.onShareBackup(ready.file)
                backupViewModel.onReadyDismissed()
            },
            // The dialog stays until a target is picked: see BackupViewModel.onSaveTargetPicked.
            onSave = { activity.onSaveBackup(ready.file) },
            onDismiss = backupViewModel::onReadyDismissed,
        )
    }
    if (backup is BackupUiState.ConfirmRestore) {
        ConfirmDialog(
            text = stringResource(Res.string.backup_restore_confirm),
            confirm = stringResource(Res.string.backup_restore_action),
            onConfirm = backupViewModel::onRestoreConfirmed,
            onDismiss = backupViewModel::onRestoreDismissed,
        )
    }
    (backup as? BackupUiState.PlaylistConflict)?.let { conflict ->
        PlaylistConflictDialog(
            names = conflict.names,
            onChoice = backupViewModel::onPlaylistConflictResolved,
            onDismiss = backupViewModel::onPlaylistConflictDismissed,
        )
    }
}

private const val PLAYER_TRANSITION_MS = 320

/** Material's "expanded" window class: from here the player gets its own pane. */
private val WIDE_LAYOUT_MIN_WIDTH = 840.dp
private const val PLAYER_PANE_FRACTION = 0.42f
private val PLAYER_PANE_MIN = 420.dp
private val PLAYER_PANE_MAX = 560.dp
