package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.loops.BeatGrid
import com.damianogiusti.slowdowner.domain.tempo.TapTempo
import com.damianogiusti.slowdowner.ui.common.formatDecimal
import com.damianogiusti.slowdowner.ui.common.reportsTextInputFocus
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * The tempo of the open track: the one detected from the audio, or the user's own, typed or tapped
 * (Tap, or T in the field). Save, Clear (null: back to the detected tempo), Cancel. Opened from the
 * BPM in the header's meta line.
 */
@Composable
fun BpmDialog(
    currentBpm: Float?,
    detectedBpm: Float? = null,
    onConfirm: (bpm: Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialText = remember(currentBpm, detectedBpm) { bpmPrefill(currentBpm, detectedBpm) }
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val tapTempo = remember {
        val start = TimeSource.Monotonic.markNow()
        TapTempo { start.elapsedNow().inWholeMilliseconds }
    }
    val onTap: () -> Unit = {
        tapTempo.tap()?.let { tapped ->
            val text = bpmText(tapped)
            value = TextFieldValue(text, selection = TextRange(text.length))
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val parsed = parseBpm(value.text)
    // Out of range is worth saying while typing; an unfinished or empty number is not.
    val isOutOfRange = parsed != null && !BeatGrid.isValidBpm(parsed)
    val canSave = parsed != null && !isOutOfRange
    val min = BeatGrid.MIN_BPM.toInt()
    val max = BeatGrid.MAX_BPM.toInt()
    // The tempo in use, untouched: nothing to save. Saving it would make a detected tempo a typed one,
    // rounded to a tenth (its beat grid drifting from the music's), or round a typed one again.
    val save: () -> Unit = { if (isPrefilledBpm(currentBpm, detectedBpm, value.text)) onDismiss() else onConfirm(parsed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.bpm_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.bpm_dialog_message, min, max),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = if (detectedBpm != null) 8.dp else 16.dp),
                )
                if (detectedBpm != null) {
                    Text(
                        stringResource(Res.string.bpm_detected, bpmText(detectedBpm)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        keyboardActions = KeyboardActions(onDone = { if (canSave) save() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .reportsTextInputFocus()
                            // T taps along without leaving the field (letters are not typed in it anyway).
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.T && event.type == KeyEventType.KeyDown) {
                                    onTap()
                                    true
                                } else {
                                    false
                                }
                            },
                    )
                    // Not focusable: tapping it keeps the focus (and the shortcut guard) in the field.
                    Button(onClick = { onTap() }, modifier = Modifier.focusProperties { canFocus = false }) {
                        Text(stringResource(Res.string.bpm_tap))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = canSave) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = {
            Row {
                if (currentBpm != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(if (detectedBpm != null) Res.string.bpm_use_detected else Res.string.bpm_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
    )
}

/** "92", "92.5", "92,5" (decimal comma): the tempo, or null while it is not a number yet. */
internal fun parseBpm(text: String): Float? =
    text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

/** The field's starting text: the typed tempo, else the detected one, else empty. */
internal fun bpmPrefill(currentBpm: Float?, detectedBpm: Float?): String = (currentBpm ?: detectedBpm)?.let(::bpmText).orEmpty()

/** Whether [text] is still the field's starting text, the tempo in use: Save then changes nothing. */
internal fun isPrefilledBpm(currentBpm: Float?, detectedBpm: Float?, text: String): Boolean =
    text.isNotEmpty() && text == bpmPrefill(currentBpm, detectedBpm)

/** Whole tempos without decimals. */
private fun bpmText(bpm: Float): String =
    if (bpm == bpm.roundToInt().toFloat()) bpm.roundToInt().toString() else formatDecimal(bpm)
