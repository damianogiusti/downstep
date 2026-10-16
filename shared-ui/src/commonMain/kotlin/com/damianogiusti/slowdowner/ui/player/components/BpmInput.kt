package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group C): final signature, minimal body. See ui-contract.md.

/**
 * Enter the tempo of the open track (there is no detection): a number field, Save, Clear (null),
 * Cancel. Opened from "BPM —" in the header's meta line.
 */
@Composable
fun BpmDialog(
    currentBpm: Float?,
    onConfirm: (bpm: Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bpm_dialog_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
