package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.export.ExportScope
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme

/** One bar and one Cancel for the whole batch: [fileCount] files. */
@Composable
fun ExportBanner(fraction: Float, onCancel: () -> Unit, modifier: Modifier = Modifier, fileCount: Int = 1) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(Res.drawable.ic_share), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                val percent = (fraction * 100).toInt()
                val title = if (fileCount > 1) {
                    stringResource(Res.string.export_running_many, pluralStringResource(Res.plurals.export_files_count, fileCount, fileCount), percent)
                } else {
                    stringResource(Res.string.export_running, percent)
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.export_running_hint),
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

/** What to export: the full track, the loops (one file each) or both. Loops need at least one loop. */
@Composable
fun ExportOptionsDialog(
    scope: ExportScope,
    loopCount: Int,
    onScopeChange: (ExportScope) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_share), contentDescription = null) },
        title = { Text(stringResource(Res.string.export_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.export_options_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.selectableGroup()) {
                    val loopsHint = if (loopCount > 0) null else stringResource(Res.string.export_scope_no_loops)
                    ScopeRow(
                        label = stringResource(Res.string.export_scope_full),
                        hint = pluralStringResource(Res.plurals.export_files_count, 1, 1),
                        isSelected = scope == ExportScope.FULL_TRACK,
                        isEnabled = true,
                        onClick = { onScopeChange(ExportScope.FULL_TRACK) },
                    )
                    ScopeRow(
                        label = stringResource(Res.string.export_scope_loops),
                        hint = loopsHint ?: pluralStringResource(Res.plurals.export_files_count, loopCount, loopCount),
                        isSelected = scope == ExportScope.LOOPS,
                        isEnabled = loopCount > 0,
                        onClick = { onScopeChange(ExportScope.LOOPS) },
                    )
                    ScopeRow(
                        label = stringResource(Res.string.export_scope_both),
                        hint = loopsHint ?: pluralStringResource(Res.plurals.export_files_count, loopCount + 1, loopCount + 1),
                        isSelected = scope == ExportScope.BOTH,
                        isEnabled = loopCount > 0,
                        onClick = { onScopeChange(ExportScope.BOTH) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.export)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun ScopeRow(label: String, hint: String, isSelected: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, enabled = isEnabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
            .padding(vertical = 4.dp),
    ) {
        // The row handles the click: the button only shows the state.
        RadioButton(selected = isSelected, onClick = null, enabled = isEnabled)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One file: its name. A batch: how many, and the first few names. */
@Composable
fun ExportReadyDialog(files: List<ExportedFile>, onShare: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_share), contentDescription = null) },
        title = { Text(stringResource(Res.string.export_ready_title)) },
        text = {
            val single = files.singleOrNull()
            if (single != null) {
                Text(stringResource(Res.string.export_ready_text, single.name))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.export_ready_text_many, pluralStringResource(Res.plurals.export_files_count, files.size, files.size)))
                    files.take(MAX_LISTED_FILES).forEach { file ->
                        Text(
                            file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (files.size > MAX_LISTED_FILES) {
                        Text(
                            stringResource(Res.string.export_ready_more, files.size - MAX_LISTED_FILES),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                val save = if (files.size > 1) Res.string.export_save_folder else Res.string.export_save
                TextButton(onClick = onSave) { Text(stringResource(save)) }
                TextButton(onClick = onShare) { Text(stringResource(Res.string.export_share)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

private const val MAX_LISTED_FILES = 5
private const val DISABLED_ALPHA = 0.5f
