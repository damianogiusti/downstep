package com.damianogiusti.slowdowner.ui.update

import com.damianogiusti.slowdowner.ui.common.TitleBarOverlay
import com.damianogiusti.slowdowner.ui.common.titleBarControl
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
import androidx.compose.ui.unit.Dp
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
import com.damianogiusti.slowdowner.ui.resources.update_restarting
import com.damianogiusti.slowdowner.ui.resources.update_retry
import com.damianogiusti.slowdowner.ui.resources.update_title
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
    if (available.install == UpdateInstallState.Restarting) LaunchedEffect(Unit) { onRestart() }
    if (!available.isDialogOpen) return
    val uriHandler = LocalUriHandler.current
    UpdateDialog(
        state = available,
        onInstall = viewModel::onInstallClicked,
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
fun UpdateBanner(viewModel: UpdateViewModel = koinViewModel(), endInset: Dp = 0.dp) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val available = state as? UpdateUiState.Available ?: return
    UpdateBannerContent(version = available.version, onClick = viewModel::onBannerClicked, endInset = endInset)
}

/** [endInset]: room left at the right end, under the window buttons when the banner is the window's top. */
@Composable
internal fun UpdateBannerContent(version: String, onClick: () -> Unit, endInset: Dp = 0.dp) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // All of it opens the dialog: a control, not a place to drag the window by.
        modifier = Modifier.fillMaxWidth().titleBarControl().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp + endInset),
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
    onOpenPage: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    val isBusy = state.install is UpdateInstallState.Downloading || state.install == UpdateInstallState.Restarting
    TitleBarOverlay()
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
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                )
                InstallProgress(state.install)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DialogButtons(state, onInstall, onOpenPage, onCancel, onLater)
                }
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
        } else if (!isError) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun DialogButtons(
    state: UpdateUiState.Available,
    onInstall: () -> Unit,
    onOpenPage: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
) {
    when (val install = state.install) {
        // Once the download is complete the new app is being put in place: no way back.
        is UpdateInstallState.Downloading ->
            if (install.percent < 100) TextButton(onClick = onCancel) { Text(stringResource(Res.string.update_cancel)) }
        UpdateInstallState.Restarting -> Unit
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
