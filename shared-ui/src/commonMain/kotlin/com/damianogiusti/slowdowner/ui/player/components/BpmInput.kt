package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.ui.common.formatDecimal
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
    val initialText = remember(currentBpm) { currentBpm?.let(::bpmText).orEmpty() }
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val parsed = parseBpm(value.text)
    // Out of range is worth saying while typing; an unfinished or empty number is not.
    val isOutOfRange = parsed != null && !BeatGrid.isValidBpm(parsed)
    val canSave = parsed != null && !isOutOfRange
    val min = BeatGrid.MIN_BPM.toInt()
    val max = BeatGrid.MAX_BPM.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bpm_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.bpm_dialog_message, min, max),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { new -> if (new.text.all { it.isDigit() || it == '.' || it == ',' }) value = new },
                    label = { Text(stringResource(Res.string.bpm_label)) },
                    singleLine = true,
                    isError = isOutOfRange,
                    supportingText = if (isOutOfRange) {
                        { Text(stringResource(Res.string.error_bpm_range, min, max)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(parsed) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .reportsTextInputFocus(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(parsed) }, enabled = canSave) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                if (currentBpm != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(Res.string.bpm_clear)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

/** "92", "92.5", "92,5" (decimal comma): the tempo, or null while it is not a number yet. */
internal fun parseBpm(text: String): Float? =
    text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

/** The field's starting text: whole tempos without decimals. */
private fun bpmText(bpm: Float): String =
    if (bpm == bpm.roundToInt().toFloat()) bpm.roundToInt().toString() else formatDecimal(bpm)
