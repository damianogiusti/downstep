package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.damianogiusti.slowdowner.ui.common.SectionLabel
import com.damianogiusti.slowdowner.ui.common.SettingsCard
import com.damianogiusti.slowdowner.ui.player.NotesActions
import com.damianogiusti.slowdowner.ui.player.NotesCardState
import com.damianogiusti.slowdowner.ui.player.ProgressionActions
import com.damianogiusti.slowdowner.ui.player.ProgressionCardState
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.stringResource

// PLACEHOLDER (group C): final signatures, minimal bodies. See ui-contract.md, README §2.8.

/** Progression of the active loop: switch, sentence of pill-steppers, status line, from→to track. */
@Composable
fun ProgressionCard(
    state: ProgressionCardState,
    actions: ProgressionActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        SectionLabel("${stringResource(Res.string.progression)} · ${state.loopName}")
        Text(stringResource(Res.string.progression_off), style = SlowDownerTheme.type.control)
    }
}

/** Free-text notes of the active loop; keeps its own text while typing, saved by the ViewModel after a pause. */
@Composable
fun NotesCard(
    state: NotesCardState,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier) {
        SectionLabel("${stringResource(Res.string.notes)} · ${state.loopName}")
        Text(state.notes.ifEmpty { stringResource(Res.string.notes_placeholder) }, style = SlowDownerTheme.type.body)
    }
}
