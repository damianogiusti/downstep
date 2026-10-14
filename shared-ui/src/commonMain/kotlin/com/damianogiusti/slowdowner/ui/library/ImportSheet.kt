package com.damianogiusti.slowdowner.ui.library

import com.damianogiusti.slowdowner.ui.common.isDesktop
import org.jetbrains.compose.resources.DrawableResource
import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    onFile: () -> Unit,
    onLink: () -> Unit,
    onSpotify: (() -> Unit)?,
    onRestoreBackup: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.import_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (isDesktop) Res.string.import_subtitle_desktop else Res.string.import_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ImportOption(
                icon = Res.drawable.ic_folder,
                title = stringResource(Res.string.import_file),
                description = stringResource(if (isDesktop) Res.string.import_file_description_desktop else Res.string.import_file_description),
                accent = MaterialTheme.colorScheme.primary,
                tint = SlowDownerTheme.colors.primaryTint,
                onClick = onFile,
            )
            ImportOption(
                icon = Res.drawable.ic_link,
                title = stringResource(Res.string.import_link),
                description = stringResource(Res.string.import_link_description),
                accent = MaterialTheme.colorScheme.tertiary,
                tint = SlowDownerTheme.colors.tertiaryTint,
                onClick = onLink,
            )
            if (onSpotify != null) {
                ImportOption(
                    icon = Res.drawable.ic_music_note,
                    title = stringResource(Res.string.spotify),
                    description = stringResource(Res.string.import_spotify_description),
                    accent = MaterialTheme.colorScheme.secondary,
                    tint = SlowDownerTheme.colors.primaryTint,
                    onClick = onSpotify,
                )
            }
            ImportOption(
                icon = Res.drawable.ic_unarchive,
                title = stringResource(Res.string.backup_restore),
                description = stringResource(Res.string.backup_restore_description),
                accent = MaterialTheme.colorScheme.secondary,
                tint = SlowDownerTheme.colors.tertiaryTint,
                onClick = onRestoreBackup,
            )
            // Sharing from the YouTube app exists only on the phone.
            if (!isDesktop) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(Res.string.import_share_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImportOption(icon: DrawableResource, title: String, description: String, accent: Color, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp).background(tint, CircleShape)) {
            Icon(painterResource(icon), contentDescription = null, tint = accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ImportBanner(state: ImportUiState, modifier: Modifier = Modifier) {
    if (state == ImportUiState.Idle) return
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            when (state) {
                is ImportUiState.Downloading -> stringResource(Res.string.import_downloading, state.title)
                else -> stringResource(Res.string.import_resolving)
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        val fraction = (state as? ImportUiState.Downloading)?.fraction
        val progressModifier = Modifier.fillMaxWidth()
        if (fraction == null) {
            androidx.compose.material3.LinearProgressIndicator(modifier = progressModifier, trackColor = SlowDownerTheme.colors.timelineTrack)
        } else {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { fraction },
                modifier = progressModifier,
                trackColor = SlowDownerTheme.colors.timelineTrack,
            )
        }
    }
}
