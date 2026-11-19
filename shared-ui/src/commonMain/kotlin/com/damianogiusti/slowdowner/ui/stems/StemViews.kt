package com.damianogiusti.slowdowner.ui.stems

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.resources.*
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The download of the model or the separation in progress, in the bottom bar next to the import and backup banners. */
@Composable
fun StemBanner(progress: StemProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val (title, percent) = when (progress) {
        StemProgress.None -> return
        is StemProgress.DownloadingModel -> stringResource(Res.string.stems_downloading, progress.percent) to progress.percent
        is StemProgress.Separating -> stringResource(Res.string.stems_separating, progress.percent) to progress.percent
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(Res.drawable.ic_layers), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
        }
        LinearProgressIndicator(
            progress = { percent / 100f },
            trackColor = SlowDownerTheme.colors.timelineTrack,
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        )
    }
}

/** First use: what is downloaded, how big and from where. On a deprecated Mac it adds the notice. */
@Composable
fun StemModelDialog(isDeprecatedPlatform: Boolean, onDownload: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_layers), contentDescription = null) },
        title = { Text(stringResource(Res.string.stems_model_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.stems_model_text))
                if (isDeprecatedPlatform) {
                    Text(
                        stringResource(Res.string.stems_deprecated_notice),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text(stringResource(Res.string.stems_model_download)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** The model is there, but the Mac is deprecated for the separation: asks before the first one of the launch. */
@Composable
fun StemDeprecatedDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Res.drawable.ic_layers), contentDescription = null) },
        title = { Text(stringResource(Res.string.stems_deprecated_title)) },
        text = { Text(stringResource(Res.string.stems_deprecated_notice)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.stems_deprecated_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
