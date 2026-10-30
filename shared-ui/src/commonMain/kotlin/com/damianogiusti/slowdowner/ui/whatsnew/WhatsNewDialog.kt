package com.damianogiusti.slowdowner.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.whats_new_ok
import com.damianogiusti.slowdowner.ui.resources.whats_new_title
import com.damianogiusti.slowdowner.ui.resources.whats_new_version
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Shows the popup when it is due. Place it once, over the whole app, before the support prompt and the update dialog. */
@Composable
fun WhatsNewHost(viewModel: WhatsNewViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state as? WhatsNewUiState.Visible ?: return
    WhatsNewDialog(state = visible, onClose = viewModel::onCloseClicked)
}

/** The notes are in English whatever the app's language: they are written once, for every release. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewDialog(state: WhatsNewUiState.Visible, onClose: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onClose) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.whats_new_title), style = MaterialTheme.typography.headlineSmall)
                // Several skipped versions make a long list: it scrolls, the button stays in view.
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp).padding(top = 16.dp, bottom = 24.dp),
                ) {
                    items(state.sections, key = { it.version }) { section -> SectionContent(section) }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = onClose) { Text(stringResource(Res.string.whats_new_ok)) }
                }
            }
        }
    }
}

@Composable
private fun SectionContent(section: WhatsNewSection) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.whats_new_version, section.version), style = MaterialTheme.typography.titleSmall)
        section.lines.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}
