package com.damianogiusti.slowdowner.ui.navigation

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.damianogiusti.slowdowner.ui.player.ExportUiState
import com.damianogiusti.slowdowner.ui.player.PlayerNavigation
import com.damianogiusti.slowdowner.ui.player.PlayerScreen
import com.damianogiusti.slowdowner.ui.player.PlayerViewModel
import com.damianogiusti.slowdowner.ui.player.components.ExportReadyDialog
import com.damianogiusti.slowdowner.ui.playlists.AddToPlaylistSheet
import com.damianogiusti.slowdowner.ui.playlists.PlaylistNavigation
import com.damianogiusti.slowdowner.ui.playlists.PlaylistScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

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
 * Root of the UI: the player is the screen (Downstep 2.0), the library a drawer over it, and a
 * playlist opens on top. Also the import and backup progress, their dialogs and the snackbars.
 *
 * It observes no player state: each part of the player collects its own (see PlayerScreen).
 */
@Composable
fun SlowDownerApp(viewModels: ActivityViewModels, activity: ActivityActions) {
    val playerViewModel = viewModels.player
    val importViewModel = viewModels.imports
    val backupViewModel = viewModels.backup
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var trackForPlaylist by remember { mutableStateOf<LibraryTrack?>(null) }

    CollectMessages(playerViewModel.messages, snackbarHostState)
    CollectMessages(importViewModel.messages, snackbarHostState)
    CollectMessages(backupViewModel.messages, snackbarHostState)

    LaunchedEffect(playerViewModel, activity) {
        playerViewModel.transport.map { it.isPlaying }.distinctUntilChanged().filter { it }.collect { activity.onPlaybackStarted() }
    }
    LaunchedEffect(importViewModel, playerViewModel) { importViewModel.importedTracks.collect(playerViewModel::onTrackSelected) }

    val playerNavigation = remember(playerViewModel, importViewModel, backupViewModel, activity, navController) {
        PlayerNavigation(
            onPlayStop = activity.onPlayStop,
            onAddToPlaylist = { trackForPlaylist = it },
            library = LibraryNavigation(
                onTrackClick = playerViewModel::onTrackSelected,
                onPlaylistClick = {
                    playerViewModel.onDrawerClosed()
                    navController.navigate(PlaylistRoute(it.id)) { launchSingleTop = true }
                },
                onAddToPlaylist = { trackForPlaylist = it },
                onImportFile = activity.onPickFile,
                onImportSpotify = activity.onOpenSpotify,
                onImportLink = importViewModel::onTextShared,
                onRestoreBackup = activity.onPickBackup,
                onExportTrackBackup = backupViewModel::onExportTrack,
                onExportLibraryBackup = backupViewModel::onExportLibrary,
                onDonate = activity.onDonate,
            ),
        )
    }

    Scaffold(
        // Screens handle their own insets; this one only stacks the bottom bar.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { ProgressBanners(importViewModel, backupViewModel) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = PlayerRoute,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable<PlayerRoute> {
                PlayerScreen(viewModel = playerViewModel, navigation = playerNavigation, snackbarHostState = snackbarHostState)
            }
            composable<PlaylistRoute>(
                enterTransition = { slideInHorizontally(tween(TRANSITION_MS)) { it } + fadeIn(tween(TRANSITION_MS)) },
                exitTransition = { fadeOut(tween(TRANSITION_MS)) },
                popExitTransition = { slideOutHorizontally(tween(TRANSITION_MS)) { it } + fadeOut(tween(TRANSITION_MS)) },
            ) {
                PlaylistScreen(
                    navigation = PlaylistNavigation(
                        onBack = { navController.popBackStack() },
                        onTrackClick = { track -> openFromPlaylist(navController, playerViewModel, track) },
                        onExportBackup = backupViewModel::onExportPlaylist,
                        onExportTrackBackup = backupViewModel::onExportTrack,
                    ),
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    trackForPlaylist?.let { track ->
        AddToPlaylistSheet(track = track, snackbarHostState = snackbarHostState, onDismiss = { trackForPlaylist = null })
    }
    ExportReadyHost(playerViewModel, activity)
    BackupDialogsHost(backupViewModel, activity)
}

/** Back to the player with the track loading. */
private fun openFromPlaylist(navController: NavHostController, player: PlayerViewModel, track: LibraryTrack) {
    player.onTrackSelected(track)
    navController.popBackStack(PlayerRoute, inclusive = false)
}

@Composable
private fun ProgressBanners(importViewModel: ImportViewModel, backupViewModel: BackupViewModel) {
    val import by importViewModel.state.collectAsStateWithLifecycle()
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        BackupBanner(backup, onCancelExport = backupViewModel::onExportCancelClicked)
        ImportBanner(import)
    }
}

@Composable
private fun ExportReadyHost(playerViewModel: PlayerViewModel, activity: ActivityActions) {
    val export by playerViewModel.export.collectAsStateWithLifecycle()
    (export as? ExportUiState.Ready)?.let { ready ->
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
}

@Composable
private fun BackupDialogsHost(backupViewModel: BackupViewModel, activity: ActivityActions) {
    val backup by backupViewModel.state.collectAsStateWithLifecycle()
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

private const val TRANSITION_MS = 280
