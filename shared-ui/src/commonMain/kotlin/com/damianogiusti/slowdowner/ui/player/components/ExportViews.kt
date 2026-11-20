package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.ui.player.ExportTarget
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** What the export button offers: with the loop on, a menu adds the loop alone and a new track from it. */
class ExportButtonState(val isEnabled: Boolean, val isLoopAvailable: Boolean)

class ExportMenuActions(
    val onExportWholeTrack: () -> Unit,
    val onExportLoop: () -> Unit,
    val onNewTrackFromLoop: () -> Unit,
)

@Composable
fun ExportButton(state: ExportButtonState, actions: ExportMenuActions) {
    var isMenuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { if (state.isLoopAvailable) isMenuOpen = true else actions.onExportWholeTrack() },
            enabled = state.isEnabled,
        ) {
            Icon(painterResource(Res.drawable.ic_share), stringResource(Res.string.export))
        }
        DropdownMenu(expanded = isMenuOpen && state.isEnabled, onDismissRequest = { isMenuOpen = false }) {
            val items = listOf(
                Res.string.export_whole_track to actions.onExportWholeTrack,
                Res.string.export_loop to actions.onExportLoop,
                Res.string.loop_track_new to actions.onNewTrackFromLoop,
            )
            items.forEach { (label, onClick) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        isMenuOpen = false
                        onClick()
                    },
                )
            }
        }
    }
}

@Composable
fun ExportBanner(fraction: Float, target: ExportTarget, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(Res.drawable.ic_share), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                val (title, hint) = when (target) {
                    ExportTarget.FILE -> Res.string.export_running to Res.string.export_running_hint
                    ExportTarget.LIBRARY -> Res.string.loop_track_running to Res.string.loop_track_running_hint
                }
                Text(stringResource(title, (fraction * 100).toInt()), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
        }
        LinearProgressIndicator(
            progress = { fraction },
            trackColor = SlowDownerTheme.colors.timelineTrack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
        )
    }
}

@Composable
fun ExportReadyDialog(file: ExportedFile, onShare: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_share), contentDescription = null) },
        title = { Text(stringResource(Res.string.export_ready_title)) },
        text = { Text(stringResource(Res.string.export_ready_text, file.name)) },
        confirmButton = {
            Row {
                TextButton(onClick = onSave) { Text(stringResource(Res.string.export_save)) }
                TextButton(onClick = onShare) { Text(stringResource(Res.string.export_share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
