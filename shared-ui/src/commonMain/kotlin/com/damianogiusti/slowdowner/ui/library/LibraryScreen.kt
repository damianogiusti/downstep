package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.common.isDesktop
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.ui.common.CollectMessages
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.common.EmptyState
import com.damianogiusti.slowdowner.ui.common.PlaylistArtwork
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.TrackRow
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.koin.compose.viewmodel.koinViewModel

/** Callbacks that leave the library screen or need the activity. */
class LibraryNavigation(
    val onTrackClick: (LibraryTrack) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onAddToPlaylist: (LibraryTrack) -> Unit,
    val onImportFile: () -> Unit,
    val onImportLink: (String) -> Unit,
    val onImportSpotify: (() -> Unit)?,
    val onRestoreBackup: () -> Unit,
    val onExportTrackBackup: (LibraryTrack) -> Unit,
    val onExportLibraryBackup: () -> Unit,
    /** Null hides the Donate item (see ActivityActions.onDonate). */
    val onDonate: (() -> Unit)?,
)

@Composable
fun LibraryScreen(
    navigation: LibraryNavigation,
    snackbarHostState: SnackbarHostState,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages, snackbarHostState)

    var tab by rememberSaveable { mutableStateOf(LibraryTab.Tracks) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
    var trackToRemove by remember { mutableStateOf<LibraryTrack?>(null) }

    LibraryContent(
        state = state,
        tab = tab,
        actions = LibraryContentActions(
            onTabSelected = { tab = it },
            onQueryChanged = viewModel::onQueryChanged,
            onTrackClick = navigation.onTrackClick,
            onAddToPlaylist = navigation.onAddToPlaylist,
            onExportTrackBackup = navigation.onExportTrackBackup,
            onExportLibraryBackup = navigation.onExportLibraryBackup,
            onRemoveTrack = { trackToRemove = it },
            onPlaylistClick = navigation.onPlaylistClick,
            onNewPlaylist = { showNewPlaylist = true },
            onImport = { showImport = true },
            onDonate = navigation.onDonate,
        ),
    )

    if (showImport) {
        ImportSheet(
            onFile = {
                showImport = false
                navigation.onImportFile()
            },
            onLink = {
                showImport = false
                showLinkDialog = true
            },
            onSpotify = navigation.onImportSpotify?.let { open ->
                {
                    showImport = false
                    open()
                }
            },
            onRestoreBackup = {
                showImport = false
                navigation.onRestoreBackup()
            },
            onDismiss = { showImport = false },
        )
    }
    if (showLinkDialog) {
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.import_link_title),
                label = stringResource(Res.string.import_link_label),
                confirm = stringResource(Res.string.import_action),
            ),
            initialText = "",
            onConfirm = {
                showLinkDialog = false
                navigation.onImportLink(it)
            },
            onDismiss = { showLinkDialog = false },
        )
    }
    if (showNewPlaylist) {
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(Res.string.playlist_new),
                label = stringResource(Res.string.name),
                confirm = stringResource(Res.string.create),
            ),
            initialText = "",
            onConfirm = {
                showNewPlaylist = false
                viewModel.onCreatePlaylist(it)
            },
            onDismiss = { showNewPlaylist = false },
        )
    }
    trackToRemove?.let { track ->
        ConfirmDialog(
            text = stringResource(Res.string.remove_confirm, track.title),
            confirm = stringResource(Res.string.remove),
            onConfirm = {
                viewModel.onTrackRemoveConfirmed(track)
                trackToRemove = null
            },
            onDismiss = { trackToRemove = null },
        )
    }
}

enum class LibraryTab { Tracks, Playlists }

class LibraryContentActions(
    val onTabSelected: (LibraryTab) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onTrackClick: (LibraryTrack) -> Unit,
    val onAddToPlaylist: (LibraryTrack) -> Unit,
    val onExportTrackBackup: (LibraryTrack) -> Unit,
    val onExportLibraryBackup: () -> Unit,
    val onRemoveTrack: (LibraryTrack) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onNewPlaylist: () -> Unit,
    val onImport: () -> Unit,
    val onDonate: (() -> Unit)? = null,
)

@Composable
fun LibraryContent(state: LibraryUiState, tab: LibraryTab, actions: LibraryContentActions) {
    var isSearching by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onImport,
                icon = { Icon(painterResource(Res.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(Res.string.import_action)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LibraryHeader(
                state = state,
                isSearching = isSearching,
                onSearchToggled = {
                    if (isSearching) actions.onQueryChanged("")
                    isSearching = !isSearching
                    if (isSearching) actions.onTabSelected(LibraryTab.Tracks)
                },
                onQueryChanged = actions.onQueryChanged,
                onLibraryBackup = actions.onExportLibraryBackup,
                onDonate = actions.onDonate,
            )
            LibraryTabs(tab, actions.onTabSelected)
            when (tab) {
                LibraryTab.Tracks -> TracksTab(state, actions)
                LibraryTab.Playlists -> PlaylistsTab(state.playlists, actions)
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    state: LibraryUiState,
    isSearching: Boolean,
    onSearchToggled: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onLibraryBackup: () -> Unit,
    onDonate: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSearching) {
                TextField(
                    value = state.query,
                    onValueChange = onQueryChanged,
                    placeholder = { Text(stringResource(Res.string.search_hint)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f).reportsTextInputFocus(),
                )
            } else {
                Text(stringResource(Res.string.library), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = onSearchToggled) {
                Icon(
                    painterResource(if (isSearching) Res.drawable.ic_close else Res.drawable.ic_search),
                    contentDescription = stringResource(if (isSearching) Res.string.search_close else Res.string.search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isSearching) LibraryMenu(onLibraryBackup, onDonate)
        }
        if (!isSearching) {
            Text(
                if (state.totalTracks == 0) {
                    stringResource(Res.string.library_no_tracks_yet)
                } else {
                    stringResource(
                        Res.string.library_summary,
                        pluralStringResource(Res.plurals.tracks_count, state.totalTracks, state.totalTracks),
                        pluralStringResource(Res.plurals.playlists_count, state.playlists.size, state.playlists.size),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryTabs(selected: LibraryTab, onSelected: (LibraryTab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
            .padding(4.dp),
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                selected = isSelected,
                onClick = { onSelected(tab) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = content,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(if (tab == LibraryTab.Tracks) Res.drawable.ic_music_note else Res.drawable.ic_queue_music),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(if (tab == LibraryTab.Tracks) Res.string.tab_tracks else Res.string.tab_playlists),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TracksTab(state: LibraryUiState, actions: LibraryContentActions) {
    if (state.isLoaded && state.totalTracks == 0) {
        EmptyState(
            icon = Res.drawable.ic_library_music,
            title = stringResource(Res.string.library_empty_title),
            message = stringResource(if (isDesktop) Res.string.library_empty_message_desktop else Res.string.library_empty_message),
            modifier = Modifier.fillMaxSize(),
        ) {
            Button(onClick = actions.onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(Res.drawable.ic_add), null, Modifier.size(20.dp))
                Text(stringResource(Res.string.import_action), Modifier.padding(start = 8.dp))
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = FAB_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Text(
                stringResource(if (state.query.isBlank()) Res.string.recently_added else Res.string.search_results).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        if (state.tracks.isEmpty()) {
            item(key = "no_results") {
                Text(
                    stringResource(Res.string.search_no_results, state.query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.tracks, key = { it.track.id }) { item ->
            TrackRow(
                track = item.track,
                practice = item.practice,
                isNowPlaying = item.track.id == state.nowPlayingId,
                onClick = { actions.onTrackClick(item.track) },
                trailing = {
                    TrackMenu(
                        onAddToPlaylist = { actions.onAddToPlaylist(item.track) },
                        onExportBackup = { actions.onExportTrackBackup(item.track) },
                        onRemove = { actions.onRemoveTrack(item.track) },
                    )
                },
            )
        }
    }
}

@Composable
private fun TrackMenu(onAddToPlaylist: () -> Unit, onExportBackup: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(Res.drawable.ic_more_vert),
                contentDescription = stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_to_playlist)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_playlist_add), null) },
                onClick = {
                    expanded = false
                    onAddToPlaylist()
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
                text = { Text(stringResource(Res.string.remove)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun LibraryMenu(onLibraryBackup: () -> Unit, onDonate: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(Res.drawable.ic_more_vert),
                contentDescription = stringResource(Res.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.backup_library)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_archive), null) },
                onClick = {
                    expanded = false
                    onLibraryBackup()
                },
            )
            onDonate?.let { donate ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.support_donate)) },
                    leadingIcon = { Icon(painterResource(Res.drawable.ic_favorite), null) },
                    onClick = {
                        expanded = false
                        donate()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(playlists: List<Playlist>, actions: LibraryContentActions) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = FAB_CLEARANCE),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(Res.string.playlists_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(playlist, onClick = { actions.onPlaylistClick(playlist) })
        }
        item(key = "new") { NewPlaylistCard(actions.onNewPlaylist) }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
    ) {
        PlaylistArtwork(
            playlistId = playlist.id,
            iconSize = 40.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(Res.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    // Same structure as PlaylistCard, so both have the same height in the grid.
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(SlowDownerTheme.colors.primaryTint, CircleShape),
            ) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(stringResource(Res.string.playlist_new), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(Res.string.playlist_new_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Room for the import button above the last row. */
private val FAB_CLEARANCE = 96.dp

@Preview(heightDp = 915)
@Composable
private fun LibraryContentPreview() {
    SlowDownerTheme(darkTheme = true) {
        LibraryContent(
            state = LibraryUiState(
                tracks = listOf(
                    TrackItem(LibraryTrack("1", "Autumn Leaves - Chet Baker", "/a", "https://y", 0), PracticeSettings(75f, -2f)),
                    TrackItem(LibraryTrack("2", "Blackbird - The Beatles", "/b", null, 0), null),
                ),
                totalTracks = 2,
                nowPlayingId = "1",
                isLoaded = true,
            ),
            tab = LibraryTab.Tracks,
            actions = LibraryContentActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
