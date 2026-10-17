package com.damianogiusti.slowdowner.ui.support

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.ic_favorite
import com.damianogiusti.slowdowner.ui.resources.support_close
import com.damianogiusti.slowdowner.ui.resources.support_close_countdown
import com.damianogiusti.slowdowner.ui.resources.support_donate
import com.damianogiusti.slowdowner.ui.resources.support_message
import com.damianogiusti.slowdowner.ui.resources.support_title
import com.damianogiusti.slowdowner.ui.resources.support_why_free
import com.damianogiusti.slowdowner.ui.resources.support_why_free_url
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Shows the prompt when it is due. Place it once, over the whole app. */
@Composable
fun SupportPromptHost(viewModel: SupportPromptViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state as? SupportPromptUiState.Visible ?: return
    val uriHandler = LocalUriHandler.current
    // A string resource, so the page follows the app language like the texts.
    val whyFreeUrl = stringResource(Res.string.support_why_free_url)
    SupportPromptDialog(
        state = visible,
        onWhyFree = {
            uriHandler.openUri(whyFreeUrl)
            viewModel.onWhyFreeClicked()
        },
        onDonate = {
            uriHandler.openUri(DONATE_URL)
            viewModel.onDonateClicked()
        },
        onClose = viewModel::onCloseClicked,
        onDismiss = viewModel::onDismissRequested,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupportPromptDialog(
    state: SupportPromptUiState.Visible,
    onWhyFree: () -> Unit,
    onDonate: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    TitleBarOverlay()
    BasicAlertDialog(
        onDismissRequest = { if (state.canClose) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = state.canClose, dismissOnClickOutside = state.canClose),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.support_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.support_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onClose, enabled = state.canClose) {
                        Text(
                            if (state.canClose) stringResource(Res.string.support_close)
                            else stringResource(Res.string.support_close_countdown, state.closeSecondsLeft),
                        )
                    }
                    OutlinedButton(onClick = onDonate, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(painterResource(Res.drawable.ic_favorite), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.support_donate))
                    }
                    // The primary action: the page explains why, and links the donation too.
                    Button(onClick = onWhyFree) { Text(stringResource(Res.string.support_why_free)) }
                }
            }
        }
    }
}
