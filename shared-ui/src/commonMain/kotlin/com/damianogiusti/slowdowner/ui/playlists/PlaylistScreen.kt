package com.damianogiusti.slowdowner.ui.playlists

import com.damianogiusti.slowdowner.ui.common.LocalWindowTitleBar
import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.common.titleBarCaption
import com.damianogiusti.slowdowner.ui.common.titleBarControl
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.ui.common.CollectMessages
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.common.EmptyState
import com.damianogiusti.slowdowner.ui.common.PlaylistArtwork
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.TrackArtwork
import com.damianogiusti.slowdowner.ui.common.TrackRow
import com.damianogiusti.slowdowner.ui.library.TrackItem
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class PlaylistNavigation(
    val onBack: () -> Unit,
    val onTrackClick: (LibraryTrack) -> Unit,
    val onExportBackup: (Playlist) -> Unit,
    val onExportTrackBackup: (LibraryTrack) -> Unit,
)

@Composable
fun PlaylistScreen(
    navigation: PlaylistNavigation,
    snackbarHostState: SnackbarHostState,
    viewModel: PlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages, snackbarHostState)
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) navigation.onBack() }

    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showAddTracks by rememberSaveable { mutableStateOf(false) }

    // Esc goes back to the player. The window's shortcuts leave it to this screen, and key events
    // reach only the focused element's ancestors: the screen takes the focus when it opens.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Escape || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                navigation.onBack()
                true
            },
    ) {
        PlaylistContent(
            state = state,
            actions = PlaylistContentActions(
                onBack = navigation.onBack,
                onTrackClick = navigation.onTrackClick,
                onPractice = { state.tracks.firstOrNull()?.let { navigation.onTrackClick(it.track) } },
                onAddTracks = { showAddTracks = true },
                onRename = { showRename = true },
                onExportBackup = { state.playlist?.let(navigation.onExportBackup) },
                onDelete = { showDelete = true },
                onExportTrackBackup = navigation.onExportTrackBackup,
                onRemoveTrack = viewModel::onRemoveTrack,
                onReorder = viewModel::onReorder,
            ),
        )
    }

    val playlist = state.playlist ?: return
    if (showRename) {
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.playlist_rename),
                label = stringResource(Res.string.name),
                confirm = stringResource(Res.string.save),
            ),
            initialText = playlist.name,
            onConfirm = {
                showRename = false
                viewModel.onRename(it)
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            text = stringResource(Res.string.playlist_delete_confirm, playlist.name),
            confirm = stringResource(Res.string.delete),
            onConfirm = {
                showDelete = false
                viewModel.onDeleteConfirmed()
            },
            onDismiss = { showDelete = false },
        )
    }
    if (showAddTracks) {
        AddTracksSheet(
            tracks = state.libraryTracks,
            selectedIds = state.trackIds,
            onToggle = viewModel::onTrackToggled,
            onDismiss = { showAddTracks = false },
        )
    }
}

class PlaylistContentActions(
    val onBack: () -> Unit,
    val onTrackClick: (LibraryTrack) -> Unit,
    val onPractice: () -> Unit,
    val onAddTracks: () -> Unit,
    val onRename: () -> Unit,
    val onExportBackup: () -> Unit,
    val onDelete: () -> Unit,
    val onExportTrackBackup: (LibraryTrack) -> Unit,
    val onRemoveTrack: (LibraryTrack) -> Unit,
    val onReorder: (List<String>) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistContent(state: PlaylistUiState, actions: PlaylistContentActions) {
    Scaffold(
        topBar = {
            // Windows: the bar is the window's title bar, with the window buttons at its right end.
            val titleBar = LocalWindowTitleBar.current
            Box(modifier = if (titleBar != null) Modifier.titleBarCaption() else Modifier) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = actions.onBack, modifier = Modifier.titleBarControl()) {
                            Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.back))
                        }
                    },
                    actions = {
                        Box(modifier = Modifier.titleBarControl()) {
                            PlaylistMenu(actions.onRename, actions.onExportBackup, actions.onDelete)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.padding(end = titleBar?.buttonsWidth ?: 0.dp),
                )
                if (titleBar != null) {
                    Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.matchParentSize()) {
                        titleBar.WindowButtons(Modifier.fillMaxHeight())
                    }
                }
            }
        },
    ) { padding ->
        val playlist = state.playlist ?: return@Scaffold
        // Local order while dragging; the database only gets the result when the drag ends.
        var items by remember(state.tracks) { mutableStateOf(state.tracks) }
        val listState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(listState) { from, to ->
            items = items.toMutableList().apply { add(to.index - HEADER_ITEMS, removeAt(from.index - HEADER_ITEMS)) }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            item(key = "header") {
                PlaylistHeader(state, actions)
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Res.drawable.ic_queue_music,
                        title = stringResource(Res.string.playlist_empty_title),
                        message = stringResource(Res.string.playlist_empty_message),
                    )
                }
            }
            itemsIndexed(items, key = { _, item -> item.track.id }) { index, item ->
                ReorderableItem(reorderState, key = item.track.id) { isDragging ->
                    val moveUp = stringResource(Res.string.move_up)
                    val moveDown = stringResource(Res.string.move_down)
                    Surface(
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = if (isDragging) 8.dp else 0.dp,
                        modifier = Modifier.semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) { move(items, index, -1, actions.onReorder) }.takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) { move(items, index, 1, actions.onReorder) }.takeIf { index < items.lastIndex },
                            )
                        },
                    ) {
                        TrackRow(
                            track = item.track,
                            practice = item.practice,
                            isNowPlaying = item.track.id == state.nowPlayingId,
                            onClick = { actions.onTrackClick(item.track) },
                            leading = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier
                                        .size(36.dp)
                                        .draggableHandle(
                                            onDragStopped = {
                                                val order = items.map { it.track.id }
                                                if (order != state.tracks.map { it.track.id }) actions.onReorder(order)
                                            },
                                        ),
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.ic_drag_indicator),
                                        contentDescription = stringResource(Res.string.reorder),
                                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailing = {
                                TrackInPlaylistMenu(
                                    canMoveUp = index > 0,
                                    canMoveDown = index < items.lastIndex,
                                    onMove = { offset -> move(items, index, offset, actions.onReorder) },
                                    onExportBackup = { actions.onExportTrackBackup(item.track) },
                                    onRemove = { actions.onRemoveTrack(item.track) },
                                )
                            },
                            modifier = Modifier.padding(start = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun move(items: List<TrackItem>, index: Int, offset: Int, onReorder: (List<String>) -> Unit): Boolean {
    val target = index + offset
    if (target !in items.indices) return false
    val ids = items.map { it.track.id }.toMutableList()
    ids.add(target, ids.removeAt(index))
    onReorder(ids)
    return true
}

@Composable
private fun PlaylistHeader(state: PlaylistUiState, actions: PlaylistContentActions) {
    val playlist = state.playlist ?: return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlaylistArtwork(playlist.id, iconSize = 44.dp, modifier = Modifier.size(112.dp), shape = MaterialTheme.shapes.medium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.playlist_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(playlist.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        pluralStringResource(Res.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
                        state.customizedTrackCount.takeIf { it > 0 }?.let {
                            pluralStringResource(Res.plurals.customized_count, it, it)
                        },
                    ).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
        ) {
            Button(onClick = actions.onPractice, enabled = state.tracks.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(painterResource(Res.drawable.ic_play), null, Modifier.size(20.dp))
                Text(stringResource(Res.string.practice), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = actions.onAddTracks,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(20.dp))
                Text(stringResource(Res.string.playlist_add_tracks), Modifier.padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlaylistMenu(onRename: () -> Unit, onExportBackup: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), stringResource(Res.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TitleBarOverlay()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.playlist_rename)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_edit), null) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.backup_export)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_archive), null) },
                onClick = {
                    expanded = false
                    onExportBackup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.playlist_delete)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun TrackInPlaylistMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onExportBackup: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(Res.drawable.ic_more_vert),
                stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TitleBarOverlay()
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.move_up)) },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_arrow_upward), null) },
                    onClick = {
                        expanded = false
                        onMove(-1)
                    },
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.move_down)) },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_arrow_downward), null) },
                    onClick = {
                        expanded = false
                        onMove(1)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.backup_export)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_archive), null) },
                onClick = {
                    expanded = false
                    onExportBackup()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.playlist_remove_track)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTracksSheet(
    tracks: List<LibraryTrack>,
    selectedIds: Set<String>,
    onToggle: (LibraryTrack, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    TitleBarOverlay()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(Res.string.playlist_add_tracks), style = MaterialTheme.typography.titleLarge)
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp).padding(top = 8.dp)) {
                items(tracks, key = { it.id }) { track ->
                    val isIn = track.id in selectedIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(track, isIn) }
                            .padding(vertical = 6.dp),
                    ) {
                        TrackArtwork(track, isNowPlaying = false, size = 44.dp)
                        Text(
                            track.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(checked = isIn, onCheckedChange = { onToggle(track, isIn) })
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(stringResource(Res.string.done))
            }
        }
    }
}

/** The header is the first item of the list: reorder indexes are shifted by it. */
private const val HEADER_ITEMS = 1
