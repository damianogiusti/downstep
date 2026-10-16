package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.common.isDesktop
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.loops.ObserveLoopsUseCase
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings
import com.damianogiusti.slowdowner.domain.theme.ThemeMode
import com.damianogiusti.slowdowner.ui.common.CollectMessages
import com.damianogiusti.slowdowner.ui.common.ConfirmDialog
import com.damianogiusti.slowdowner.ui.common.EmptyState
import com.damianogiusti.slowdowner.ui.common.PlaylistArtwork
import com.damianogiusti.slowdowner.ui.common.SectionLabel
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.theme.DownstepShapes
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
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

/**
 * The library drawer's panel (README §2.9): tracks and playlists, search, and the import, link,
 * new playlist and remove dialogs it opens. [LibraryDrawer] places it over the player.
 */
@Composable
fun LibraryScreen(
    navigation: LibraryNavigation,
    snackbarHostState: SnackbarHostState,
    tab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
    onClose: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
    observeLoops: ObserveLoopsUseCase = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages, snackbarHostState)
    // The ViewModel outlives the drawer: a search must not keep filtering a list nobody sees.
    DisposableEffect(viewModel) { onDispose { viewModel.onQueryChanged("") } }

    var showImport by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
    var trackToRemove by remember { mutableStateOf<LibraryTrack?>(null) }

    val loopCounts = remember(observeLoops) {
        LoopCounts { trackId -> observeLoops(trackId).map { it.loops.size }.distinctUntilChanged() }
    }
    val actions = remember(viewModel, navigation, onTabSelected, onClose) {
        LibraryContentActions(
            onTabSelected = onTabSelected,
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
            onClose = onClose,
        )
    }
    LibraryContent(state = state, tab = tab, actions = actions, loopCounts = loopCounts)

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

/** How many loops a track has, for the row meta ("Local file · 3 loops"). */
fun interface LoopCounts {
    fun of(trackId: String): Flow<Int>
}

@Stable
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
    val onClose: () -> Unit = {},
)

/** The panel itself: header, Tracks / Playlists pill, the list, the Import button. */
@Composable
fun LibraryContent(state: LibraryUiState, tab: LibraryTab, actions: LibraryContentActions, loopCounts: LoopCounts? = null) {
    var isSearching by remember { mutableStateOf(false) }
    val closeSearch = {
        actions.onQueryChanged("")
        isSearching = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Typing in the search field turns the window's shortcuts off, Esc included: handled here.
            .onPreviewKeyEvent { event ->
                if (event.key != Key.Escape || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isSearching) closeSearch() else actions.onClose()
                true
            },
    ) {
        DrawerHeader(
            query = state.query,
            isSearching = isSearching,
            onSearchToggled = {
                if (isSearching) {
                    closeSearch()
                } else {
                    isSearching = true
                    actions.onTabSelected(LibraryTab.Tracks)
                }
            },
            actions = actions,
        )
        LibraryTabs(tab, actions.onTabSelected)
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                LibraryTab.Tracks -> TracksTab(state, actions, loopCounts)
                LibraryTab.Playlists -> PlaylistsTab(state.playlists, actions)
            }
        }
        ImportButton(actions.onImport, Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
    }
}

@Composable
private fun DrawerHeader(query: String, isSearching: Boolean, onSearchToggled: () -> Unit, actions: LibraryContentActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    ) {
        if (isSearching) {
            SearchField(query, actions.onQueryChanged, Modifier.weight(1f))
        } else {
            Text(
                stringResource(Res.string.library),
                style = SlowDownerTheme.type.drawerTitle,
                color = SlowDownerTheme.colors.fg,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
        DrawerIconButton(
            icon = painterResource(Res.drawable.ic_d_search),
            description = stringResource(if (isSearching) Res.string.search_close else Res.string.search),
            onClick = onSearchToggled,
            selected = isSearching,
        )
        LibraryMenu(actions.onExportLibraryBackup, actions.onDonate)
        DrawerIconButton(painterResource(Res.drawable.ic_d_close), stringResource(Res.string.close), actions.onClose)
    }
}

@Composable
private fun SearchField(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val colors = SlowDownerTheme.colors
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        textStyle = SlowDownerTheme.type.body.copy(color = colors.fg),
        cursorBrush = SolidColor(colors.amber),
        modifier = modifier.focusRequester(focusRequester).reportsTextInputFocus(),
        decorationBox = { field ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(36.dp)
                    .background(colors.bg, DownstepShapes.pill)
                    .border(1.dp, colors.surface3, DownstepShapes.pill)
                    .padding(horizontal = 14.dp),
            ) {
                if (query.isEmpty()) {
                    Text(stringResource(Res.string.search_hint), style = SlowDownerTheme.type.body, color = colors.fg2, maxLines = 1)
                }
                field()
            }
        },
    )
}

/** The prototype's 36dp round, borderless header buttons; [selected] keeps the hover fill. */
@Composable
private fun DrawerIconButton(icon: Painter, description: String, onClick: () -> Unit, selected: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    Tooltip(description) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(DownstepShapes.pill)
                .background(if (selected || isHovered) SlowDownerTheme.colors.surface3 else Color.Transparent)
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Icon(icon, contentDescription = null, tint = SlowDownerTheme.colors.fg, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LibraryTabs(selected: LibraryTab, onSelected: (LibraryTab) -> Unit) {
    SegmentedPill(
        container = SlowDownerTheme.colors.bg,
        border = null,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        LibraryTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(DownstepShapes.pill)
                    .background(if (isSelected) SlowDownerTheme.colors.fg else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelected(tab) },
            ) {
                Text(
                    stringResource(if (tab == LibraryTab.Tracks) Res.string.tab_tracks else Res.string.tab_playlists),
                    style = SlowDownerTheme.type.control,
                    color = if (isSelected) SlowDownerTheme.colors.chipFgActive else SlowDownerTheme.colors.fg,
                )
            }
        }
    }
}

@Composable
private fun ListLabel(text: String) {
    SectionLabel(text, modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun TracksTab(state: LibraryUiState, actions: LibraryContentActions, loopCounts: LoopCounts?) {
    if (state.isLoaded && state.totalTracks == 0) {
        EmptyState(
            icon = Res.drawable.ic_library_music,
            title = stringResource(Res.string.library_empty_title),
            message = stringResource(if (isDesktop) Res.string.library_empty_message_desktop else Res.string.library_empty_message),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            ListLabel(stringResource(if (state.query.isBlank()) Res.string.recently_added else Res.string.search_results))
        }
        if (state.isLoaded && state.tracks.isEmpty()) {
            item(key = "no_results") {
                Text(
                    stringResource(Res.string.search_no_results, state.query),
                    style = SlowDownerTheme.type.body,
                    color = SlowDownerTheme.colors.fg2,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(state.tracks, key = { it.track.id }) { item ->
            DrawerTrackRow(item.track, isCurrent = item.track.id == state.nowPlayingId, actions, loopCounts)
        }
    }
}

/** A track: artwork tile, title, "source · N loops"; the current one in amber. Menu on ⋮ or right click. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerTrackRow(track: LibraryTrack, isCurrent: Boolean, actions: LibraryContentActions, loopCounts: LoopCounts?) {
    val colors = SlowDownerTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val loops by remember(loopCounts, track.id) { loopCounts?.of(track.id) ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    val source = stringResource(if (track.sourceUrl != null) Res.string.source_youtube else Res.string.source_local)
    val meta = when (val count = loops) {
        null -> source
        0 -> stringResource(Res.string.drawer_track_meta, source, stringResource(Res.string.loops_none))
        else -> stringResource(Res.string.drawer_track_meta, source, pluralStringResource(Res.plurals.loops_count, count, count))
    }
    DrawerRow(
        title = track.title,
        meta = meta,
        titleColor = if (isCurrent) colors.amber else colors.fg,
        background = when {
            isCurrent -> colors.nowPlayingRow
            isHovered -> colors.surface2
            else -> Color.Transparent
        },
        interaction = interaction,
        onClick = { actions.onTrackClick(track) },
        onMenu = { menuOpen = true },
        artwork = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(if (isCurrent) colors.amber else colors.surface3, DownstepShapes.artwork),
            ) {
                Icon(
                    painterResource(Res.drawable.ic_d_waveform),
                    contentDescription = null,
                    tint = if (isCurrent) colors.onAmber else colors.fg2,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        trailing = {
            // Always there on touch screens; on desktop only under the pointer, as in the design.
            if (!isDesktop || isHovered || menuOpen) {
                TrackMenu(
                    expanded = menuOpen,
                    onExpandedChange = { menuOpen = it },
                    onAddToPlaylist = { actions.onAddToPlaylist(track) },
                    onExportBackup = { actions.onExportTrackBackup(track) },
                    onRemove = { actions.onRemoveTrack(track) },
                )
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    title: String,
    meta: String,
    titleColor: Color,
    background: Color,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
    artwork: @Composable () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(DownstepShapes.row)
            .background(background)
            .hoverable(interaction)
            .then(if (onMenu != null) Modifier.secondaryClick(onMenu) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = onMenu)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        artwork()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = SlowDownerTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Right click opens the row's menu (desktop). */
private fun Modifier.secondaryClick(onClick: () -> Unit): Modifier = pointerInput(onClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onClick()
            }
        }
    }
}

@Composable
private fun TrackMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddToPlaylist: () -> Unit,
    onExportBackup: () -> Unit,
    onRemove: () -> Unit,
) {
    Box {
        DrawerIconButton(painterResource(Res.drawable.ic_d_more), stringResource(Res.string.more_options), { onExpandedChange(true) })
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            MenuItem(Res.string.add_to_playlist, Res.drawable.ic_playlist_add) {
                onExpandedChange(false)
                onAddToPlaylist()
            }
            MenuItem(Res.string.backup_export, Res.drawable.ic_archive) {
                onExpandedChange(false)
                onExportBackup()
            }
            MenuItem(Res.string.remove, Res.drawable.ic_delete) {
                onExpandedChange(false)
                onRemove()
            }
        }
    }
}

/** The drawer's ⋮: whole-library backup and, where available, Donate. */
@Composable
private fun LibraryMenu(onLibraryBackup: () -> Unit, onDonate: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DrawerIconButton(painterResource(Res.drawable.ic_d_more), stringResource(Res.string.more_options), { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(Res.string.backup_library, Res.drawable.ic_archive) {
                expanded = false
                onLibraryBackup()
            }
            onDonate?.let { donate ->
                MenuItem(Res.string.support_donate, Res.drawable.ic_favorite) {
                    expanded = false
                    donate()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: StringResource,
    icon: DrawableResource,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(painterResource(icon), null) },
        onClick = onClick,
    )
}

@Composable
private fun PlaylistsTab(playlists: List<Playlist>, actions: LibraryContentActions) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { ListLabel(stringResource(Res.string.tab_playlists)) }
        if (playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(Res.string.playlists_empty),
                    style = SlowDownerTheme.type.body,
                    color = SlowDownerTheme.colors.fg2,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        items(playlists, key = { it.id }) { playlist -> PlaylistRow(playlist, actions) }
        item(key = "new") { NewPlaylistRow(actions.onNewPlaylist) }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, actions: LibraryContentActions) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    DrawerRow(
        title = playlist.name,
        meta = pluralStringResource(Res.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
        titleColor = SlowDownerTheme.colors.fg,
        background = if (isHovered) SlowDownerTheme.colors.surface2 else Color.Transparent,
        interaction = interaction,
        onClick = { actions.onPlaylistClick(playlist) },
        onMenu = null,
        artwork = { PlaylistArtwork(playlist.id, iconSize = 18.dp, modifier = Modifier.size(40.dp), shape = DownstepShapes.artwork) },
    )
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val colors = SlowDownerTheme.colors
    DrawerRow(
        title = stringResource(Res.string.playlist_new),
        meta = stringResource(Res.string.playlist_new_hint),
        titleColor = colors.fg,
        background = if (isHovered) colors.surface2 else Color.Transparent,
        interaction = interaction,
        onClick = onClick,
        onMenu = null,
        artwork = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).border(1.dp, colors.surface4, DownstepShapes.artwork),
            ) {
                Icon(painterResource(Res.drawable.ic_d_plus), contentDescription = null, tint = colors.fg2, modifier = Modifier.size(18.dp))
            }
        },
    )
}

/** Full-width amber pill at the bottom of the drawer: the import sheet (file, link, backup). */
@Composable
private fun ImportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SlowDownerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(DownstepShapes.pill)
            .background(if (isHovered) colors.amberHover else colors.amber)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Icon(painterResource(Res.drawable.ic_d_plus), contentDescription = null, tint = colors.onAmber, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.import_action),
            style = SlowDownerTheme.type.controlStrong.copy(fontSize = 15.sp),
            color = colors.onAmber,
        )
    }
}

@Preview(widthDp = 360, heightDp = 820)
@Composable
private fun LibraryContentPreview() {
    SlowDownerTheme(ThemeMode.Dark) {
        Box(modifier = Modifier.background(SlowDownerTheme.colors.surface1)) {
            LibraryContent(
                state = LibraryUiState(
                    tracks = listOf(
                        TrackItem(LibraryTrack("1", "Easy_Blues[1]", "/a", null, 0), PracticeSettings(75f, -2f)),
                        TrackItem(LibraryTrack("2", "Little Wing", "/b", "https://y", 0), null),
                    ),
                    totalTracks = 2,
                    nowPlayingId = "1",
                    isLoaded = true,
                ),
                tab = LibraryTab.Tracks,
                actions = LibraryContentActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                loopCounts = { flowOf(if (it == "1") 3 else 0) },
            )
        }
    }
}
