package com.damianogiusti.slowdowner.ui.backup

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** Progress of a running export or restore, above the mini player. Hidden otherwise. */
@Composable
fun BackupBanner(state: BackupUiState, onCancelExport: () -> Unit, modifier: Modifier = Modifier) {
    val progress = BackupBannerProgress.of(state) ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(if (progress.isExport) Res.drawable.ic_archive else Res.drawable.ic_unarchive),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(progress.title(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    stringResource(Res.string.backup_running_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A restore is not cancellable: it would stop between copying files and saving rows.
            if (progress.isExport) TextButton(onClick = onCancelExport) { Text(stringResource(Res.string.cancel)) }
        }
        val indicatorModifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
        if (progress.total > 0) {
            LinearProgressIndicator(
                progress = { progress.done.toFloat() / progress.total },
                trackColor = SlowDownerTheme.colors.timelineTrack,
                modifier = indicatorModifier,
            )
        } else {
            LinearProgressIndicator(trackColor = SlowDownerTheme.colors.timelineTrack, modifier = indicatorModifier)
        }
    }
}

private data class BackupBannerProgress(val isExport: Boolean, val done: Int, val total: Int) {
    /** Counts only help with more than one track. */
    @Composable
    fun title(): String = when {
        total <= 1 -> stringResource(if (isExport) Res.string.backup_exporting else Res.string.backup_restoring)
        isExport -> stringResource(Res.string.backup_exporting_count, done, total)
        else -> stringResource(Res.string.backup_restoring_count, done, total)
    }

    companion object {
        fun of(state: BackupUiState) = when (state) {
            is BackupUiState.Exporting -> BackupBannerProgress(isExport = true, state.done, state.total)
            is BackupUiState.Restoring -> BackupBannerProgress(isExport = false, state.done, state.total)
            else -> null
        }
    }
}

@Composable
fun BackupReadyDialog(file: BackupFile, onShare: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_archive), contentDescription = null) },
        title = { Text(stringResource(Res.string.backup_ready_title)) },
        text = { Text(stringResource(Res.string.backup_ready_text, file.name)) },
        confirmButton = {
            Row {
                TextButton(onClick = onSave) { Text(stringResource(Res.string.export_save)) }
                TextButton(onClick = onShare) { Text(stringResource(Res.string.export_share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** Stacked: three actions do not fit in a row on a phone, and a wrapped row leaves a gap. */
@Composable
fun PlaylistConflictDialog(names: List<String>, onChoice: (PlaylistConflictChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(Res.plurals.backup_playlist_conflict_title, names.size)) },
        text = {
            Text(
                pluralStringResource(
                    Res.plurals.backup_playlist_conflict_text,
                    names.size,
                    names.joinToString { "“$it”" },
                ),
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onChoice(PlaylistConflictChoice.Overwrite) }) {
                    Text(stringResource(Res.string.backup_playlist_overwrite))
                }
                TextButton(onClick = { onChoice(PlaylistConflictChoice.Copy) }) {
                    Text(stringResource(Res.string.backup_playlist_copy))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

@Preview
@Composable
private fun PlaylistConflictDialogPreview() {
    SlowDownerTheme(darkTheme = true) {
        PlaylistConflictDialog(names = listOf("The Dunnos"), onChoice = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun BackupBannerPreview() {
    SlowDownerTheme(darkTheme = true) {
        BackupBanner(BackupUiState.Exporting(done = 3, total = 12), onCancelExport = {})
    }
}
