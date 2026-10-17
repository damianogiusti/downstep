package com.damianogiusti.slowdowner.ui.playlists

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.ui.common.CollectMessages
import com.damianogiusti.slowdowner.ui.common.PlaylistArtwork
import com.damianogiusti.slowdowner.ui.common.TextInputDialog
import com.damianogiusti.slowdowner.ui.common.TextInputDialogTexts
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(track: LibraryTrack, snackbarHostState: SnackbarHostState, onDismiss: () -> Unit) {
    val viewModel: AddToPlaylistViewModel = koinViewModel()
    LaunchedEffect(track.id) { viewModel.onTrackChanged(track.id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectMessages(viewModel.messages, snackbarHostState)
    var showNewPlaylist by rememberSaveable { mutableStateOf(false) }

    TitleBarOverlay()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(Res.string.add_to_playlist), style = MaterialTheme.typography.titleLarge)
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "new") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNewPlaylist = true }
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp).background(SlowDownerTheme.colors.primaryTint, CircleShape),
                        ) {
                            Icon(painterResource(Res.drawable.ic_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(stringResource(Res.string.playlist_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    val isMember = playlist.id in state.memberOf
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onToggled(playlist) }
                            .padding(vertical = 8.dp),
                    ) {
                        PlaylistArtwork(playlist.id, iconSize = 22.dp, modifier = Modifier.size(44.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                pluralStringResource(Res.plurals.tracks_count, playlist.trackCount, playlist.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = isMember, onCheckedChange = { viewModel.onToggled(playlist) })
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(Res.string.done))
            }
        }
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
                viewModel.onCreate(it)
            },
            onDismiss = { showNewPlaylist = false },
        )
    }
}
