package com.damianogiusti.slowdowner.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.update_banner
import com.damianogiusti.slowdowner.ui.resources.update_banner_action
import com.damianogiusti.slowdowner.ui.resources.update_cancel
import com.damianogiusti.slowdowner.ui.resources.update_download
import com.damianogiusti.slowdowner.ui.resources.update_downloading
import com.damianogiusti.slowdowner.ui.resources.update_failed
import com.damianogiusti.slowdowner.ui.resources.update_install
import com.damianogiusti.slowdowner.ui.resources.update_later
import com.damianogiusti.slowdowner.ui.resources.update_message
import com.damianogiusti.slowdowner.ui.resources.update_message_install
import com.damianogiusti.slowdowner.ui.resources.update_message_manual
import com.damianogiusti.slowdowner.ui.resources.update_network_error
import com.damianogiusti.slowdowner.ui.resources.update_preparing
import com.damianogiusti.slowdowner.ui.resources.update_ready
import com.damianogiusti.slowdowner.ui.resources.update_restart_now
import com.damianogiusti.slowdowner.ui.resources.update_restarting
import com.damianogiusti.slowdowner.ui.resources.update_retry
import com.damianogiusti.slowdowner.ui.resources.update_title
import com.damianogiusti.slowdowner.ui.resources.update_whats_new
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows the dialog when it is open. Place it once, over the whole app, after the support prompt.
 * [onRestart] quits the app: the new version replaces it and opens. [installWithoutAsking] is for
 * the end-to-end check in CI, which cannot click.
 */
@Composable
fun UpdateHost(
    onRestart: () -> Unit,
    installWithoutAsking: Boolean = false,
    viewModel: UpdateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val available = state as? UpdateUiState.Available ?: return
    if (installWithoutAsking && available.canInstall) LaunchedEffect(Unit) { viewModel.onInstallClicked() }
    if (installWithoutAsking && available.install == UpdateInstallState.ReadyToRestart) {
        LaunchedEffect(Unit) { viewModel.onRestartClicked() }
    }
    if (available.install == UpdateInstallState.Restarting) LaunchedEffect(Unit) { onRestart() }
    if (!available.isDialogOpen) return
    val uriHandler = LocalUriHandler.current
    UpdateDialog(
        state = available,
        onInstall = viewModel::onInstallClicked,
        onRestart = viewModel::onRestartClicked,
        onOpenPage = {
            uriHandler.openUri(available.pageUrl)
            viewModel.onDownloadPageOpened()
        },
        onCancel = viewModel::onCancelClicked,
        onLater = viewModel::onLaterClicked,
    )
}

/** A thin strip while a newer version exists. A click opens the dialog again. */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val available = state as? UpdateUiState.Available ?: return
    UpdateBannerContent(version = available.version, onClick = viewModel::onBannerClicked)
}

@Composable
internal fun UpdateBannerContent(version: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                stringResource(Res.string.update_banner, version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(stringResource(Res.string.update_banner_action)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateDialog(
    state: UpdateUiState.Available,
    onInstall: () -> Unit,
    onRestart: () -> Unit,
    onOpenPage: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    val isBusy = state.install is UpdateInstallState.Downloading || state.install == UpdateInstallState.Restarting
    BasicAlertDialog(
        onDismissRequest = onLater,
        properties = DialogProperties(dismissOnBackPress = !isBusy, dismissOnClickOutside = !isBusy),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(Res.string.update_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(Res.string.update_message, state.version, state.installedVersion) + " " +
                        stringResource(if (state.canInstall) Res.string.update_message_install else Res.string.update_message_manual),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = if (state.notes.isEmpty()) 24.dp else 16.dp),
                )
                if (state.notes.isNotEmpty()) WhatsNew(state.notes)
                InstallProgress(state.install)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DialogButtons(state, onInstall, onRestart, onOpenPage, onCancel, onLater)
                }
            }
        }
    }
}

/** The notes are in English whatever the app's language: they are written once, for every release. */
@Composable
private fun WhatsNew(notes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        Text(stringResource(Res.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
        notes.forEach { note ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InstallProgress(install: UpdateInstallState) {
    val text = when (install) {
        is UpdateInstallState.Downloading ->
            if (install.percent >= 100) stringResource(Res.string.update_preparing)
            else stringResource(Res.string.update_downloading, install.percent)
        UpdateInstallState.Restarting -> stringResource(Res.string.update_restarting)
        UpdateInstallState.ReadyToRestart -> stringResource(Res.string.update_ready)
        UpdateInstallState.NetworkError -> stringResource(Res.string.update_network_error)
        UpdateInstallState.Failed -> stringResource(Res.string.update_failed)
        UpdateInstallState.Idle -> return
    }
    val isError = install == UpdateInstallState.NetworkError || install == UpdateInstallState.Failed
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (install is UpdateInstallState.Downloading && install.percent < 100) {
            LinearProgressIndicator(
                progress = { install.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else if (!isError && install != UpdateInstallState.ReadyToRestart) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun DialogButtons(
    state: UpdateUiState.Available,
    onInstall: () -> Unit,
    onRestart: () -> Unit,
    onOpenPage: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    when (val install = state.install) {
        // Once the download is complete the new app is being put in place: no way back.
        is UpdateInstallState.Downloading ->
            if (install.percent < 100) TextButton(onClick = onCancel) { Text(stringResource(Res.string.update_cancel)) }
        UpdateInstallState.Restarting -> Unit
        UpdateInstallState.ReadyToRestart -> {
            TextButton(onClick = onLater) { Text(stringResource(Res.string.update_later)) }
            Button(onClick = onRestart) { Text(stringResource(Res.string.update_restart_now)) }
        }
        UpdateInstallState.Failed -> {
            TextButton(onClick = onLater) { Text(stringResource(Res.string.update_later)) }
            Button(onClick = onOpenPage) { Text(stringResource(Res.string.update_download)) }
        }
        UpdateInstallState.NetworkError -> {
            TextButton(onClick = onLater) { Text(stringResource(Res.string.update_later)) }
            Button(onClick = onInstall) { Text(stringResource(Res.string.update_retry)) }
        }
        UpdateInstallState.Idle -> {
            TextButton(onClick = onLater) { Text(stringResource(Res.string.update_later)) }
            if (state.canInstall) Button(onClick = onInstall) { Text(stringResource(Res.string.update_install)) }
            else Button(onClick = onOpenPage) { Text(stringResource(Res.string.update_download)) }
        }
    }
}
