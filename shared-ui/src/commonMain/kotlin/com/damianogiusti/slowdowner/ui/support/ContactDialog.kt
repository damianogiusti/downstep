package com.damianogiusti.slowdowner.ui.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.contact_copied
import com.damianogiusti.slowdowner.ui.resources.contact_copy
import com.damianogiusti.slowdowner.ui.resources.contact_message
import com.damianogiusti.slowdowner.ui.resources.contact_no_mail_app
import com.damianogiusti.slowdowner.ui.resources.contact_title
import com.damianogiusti.slowdowner.ui.resources.contact_write
import org.jetbrains.compose.resources.stringResource

/**
 * The contact address, shown and selectable, not only behind a mail link: many Windows PCs have no
 * mail app set up, and there "Write an email" opens nothing. [onWrite] is false when no app opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDialog(
    address: String,
    onCopy: () -> Unit,
    onWrite: () -> Boolean,
    onDismiss: () -> Unit,
) {
    var isCopied by remember { mutableStateOf(false) }
    var hasNoMailApp by remember { mutableStateOf(false) }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.contact_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.contact_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                SelectionContainer {
                    Text(address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                if (hasNoMailApp) {
                    Text(
                        stringResource(Res.string.contact_no_mail_app),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    TextButton(
                        onClick = {
                            onCopy()
                            isCopied = true
                        },
                    ) { Text(stringResource(if (isCopied) Res.string.contact_copied else Res.string.contact_copy)) }
                    Button(onClick = { if (onWrite()) onDismiss() else hasNoMailApp = true }) {
                        Text(stringResource(Res.string.contact_write))
                    }
                }
            }
        }
    }
}
