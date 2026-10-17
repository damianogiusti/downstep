package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.ui.common.IconPill
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.PillToggle
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
import com.damianogiusti.slowdowner.ui.common.Tooltip
import com.damianogiusti.slowdowner.ui.common.formatClock
import com.damianogiusti.slowdowner.ui.player.AnalysisStatus
import com.damianogiusti.slowdowner.ui.player.ClickUiState
import com.damianogiusti.slowdowner.ui.player.HeaderActions
import com.damianogiusti.slowdowner.ui.player.HeaderState
import com.damianogiusti.slowdowner.ui.player.PlayerFeatures
import com.damianogiusti.slowdowner.ui.player.PresetsState
import com.damianogiusti.slowdowner.ui.player.TrackSource
import com.damianogiusti.slowdowner.ui.theme.DownstepDimens
import com.damianogiusti.slowdowner.ui.theme.SlowDownerTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Library pill (track count) · title + meta line (source · duration · BPM · loops; the BPM opens the
 * BPM dialog) · Analyze · Metronome / Count-in · Focus, Theme, Equalizer, Save preset (bookmark menu
 * with the presets), Export. Unavailable features are disabled with a tooltip (see [PlayerFeatures]).
 */
@Composable
fun PlayerHeader(
    state: HeaderState,
    trackCount: Int,
    features: PlayerFeatures,
    analysis: AnalysisStatus,
    clicks: ClickUiState,
    presets: PresetsState,
    isDarkTheme: Boolean,
    actions: HeaderActions,
    modifier: Modifier = Modifier,
) {
    val colors = SlowDownerTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 18.dp, bottom = 6.dp),
    ) {
        Pill(
            onClick = actions.onOpenLibrary,
            container = colors.surface1,
            contentPadding = PaddingValues(start = 12.dp, end = 14.dp),
            tooltip = stringResource(Res.string.library_open_description),
        ) {
            Icon(painterResource(Res.drawable.ic_h_menu), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.library), maxLines = 1)
            Text(trackCount.toString(), color = colors.fg2, style = SlowDownerTheme.type.control.copy(fontWeight = FontWeight.Normal))
        }
        TitleBlock(state, actions, Modifier.weight(1f))
        AnalyzePill(state.isOpen && !state.isLoading, features.canAnalyze, analysis, actions)
        ClickToggles(features, clicks, actions)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeaderIcon(Res.drawable.ic_h_focus, stringResource(Res.string.focus_mode), actions.onFocusToggled)
            HeaderIcon(
                if (isDarkTheme) Res.drawable.ic_h_sun else Res.drawable.ic_h_moon,
                stringResource(Res.string.theme_toggle),
                actions.onThemeToggled,
            )
            // Not in the design, which has no way to the Effects sheet: kept so the EQ is not lost.
            HeaderIcon(Res.drawable.ic_tune, stringResource(Res.string.eq_title), actions.onShowEffects)
            PresetsMenuButton(state.isOpen && !state.isLoading, presets, actions)
            HeaderIcon(Res.drawable.ic_h_export, stringResource(Res.string.export), actions.onExport, enabled = state.canExport)
        }
    }
}

@Composable
private fun TitleBlock(state: HeaderState, actions: HeaderActions, modifier: Modifier) {
    val colors = SlowDownerTheme.colors
    val title = state.title ?: stringResource(if (state.isOpen) Res.string.external_track else Res.string.no_track)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = modifier) {
        Text(title, style = SlowDownerTheme.type.title, color = colors.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val metaStyle = SlowDownerTheme.type.meta
        when {
            state.isLoading -> Text(
                stringResource(Res.string.loading_track, title),
                style = metaStyle,
                color = colors.fg2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.isOpen -> Row(verticalAlignment = Alignment.CenterVertically) {
                val source = when (state.source) {
                    TrackSource.Local -> stringResource(Res.string.source_local)
                    TrackSource.YouTube -> stringResource(Res.string.source_youtube)
                    TrackSource.External -> stringResource(Res.string.external_meta)
                    TrackSource.None -> null
                }
                val before = listOfNotNull(source, formatClock(state.durationSeconds)).joinToString(SEPARATOR, postfix = SEPARATOR)
                Text(before, style = metaStyle, color = colors.fg2, maxLines = 1)
                val bpm = state.bpm?.let { stringResource(Res.string.header_bpm, it.roundToInt().toString()) }
                    ?: stringResource(Res.string.header_bpm_unset)
                Tooltip(if (state.canEditBpm) stringResource(Res.string.bpm_edit_description) else null) {
                    Text(
                        bpm,
                        style = metaStyle,
                        color = if (state.canEditBpm && state.bpm == null) colors.fg else colors.fg2,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedBpm)
                            .clickable(enabled = state.canEditBpm, role = Role.Button, onClick = actions.onEditBpm),
                    )
                }
                val loops = if (state.loopCount == 0) stringResource(Res.string.loops_none)
                else pluralStringResource(Res.plurals.loops_count, state.loopCount, state.loopCount)
                Text(SEPARATOR + loops, style = metaStyle, color = colors.fg2, maxLines = 1)
            }
            else -> Text(stringResource(Res.string.no_track_hint), style = metaStyle, color = colors.fg2, maxLines = 1)
        }
    }
}

@Composable
private fun AnalyzePill(isOpen: Boolean, canAnalyze: Boolean, analysis: AnalysisStatus, actions: HeaderActions) {
    val colors = SlowDownerTheme.colors
    val running = analysis == AnalysisStatus.Running
    Pill(
        onClick = actions.onAnalyze,
        enabled = canAnalyze && isOpen && !running,
        container = if (running) colors.amber.copy(alpha = RUNNING_FILL_ALPHA) else colors.surface1,
        contentColor = if (running) colors.amber else colors.fg,
        border = if (running) colors.amber else colors.surface3,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        tooltip = stringResource(if (canAnalyze) Res.string.analyze_tooltip else Res.string.analyze_unavailable),
    ) {
        Icon(painterResource(Res.drawable.ic_h_sparkle), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            stringResource(
                when (analysis) {
                    AnalysisStatus.Idle -> Res.string.analyze
                    AnalysisStatus.Running -> Res.string.analyze_running
                    AnalysisStatus.Done -> Res.string.analyze_again
                },
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun ClickToggles(features: PlayerFeatures, clicks: ClickUiState, actions: HeaderActions) {
    SegmentedPill(padding = 4.dp) {
        PillToggle(
            checked = clicks.isMetronomeOn,
            onClick = actions.onMetronomeToggled,
            text = stringResource(Res.string.metronome),
            icon = painterResource(Res.drawable.ic_h_metronome),
            enabled = features.hasMetronome,
            tooltip = if (features.hasMetronome) stringResource(Res.string.metronome) else stringResource(Res.string.metronome_unavailable),
        )
        PillToggle(
            checked = clicks.isCountInOn,
            onClick = actions.onCountInToggled,
            text = stringResource(Res.string.count_in),
            icon = painterResource(Res.drawable.ic_h_count_in),
            enabled = features.hasCountIn,
            tooltip = if (features.hasCountIn) stringResource(Res.string.count_in) else stringResource(Res.string.count_in_unavailable),
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconPill(
        painterResource(icon),
        description,
        onClick,
        enabled = enabled,
        container = SlowDownerTheme.colors.surface1,
    )
}

/** The bookmark: "Save preset", the track's presets (active one checked, each deletable), "Add to playlist". */
@Composable
private fun PresetsMenuButton(isOpen: Boolean, presets: PresetsState, actions: HeaderActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HeaderIcon(Res.drawable.ic_h_bookmark, stringResource(Res.string.preset_save_title), { expanded = true }, enabled = isOpen)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SlowDownerTheme.colors.surface2,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.preset_save_title) + "…") },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_h_bookmark), contentDescription = null, modifier = Modifier.size(18.dp)) },
                enabled = presets.enabled,
                onClick = {
                    expanded = false
                    actions.onSavePreset()
                },
            )
            HorizontalDivider(color = SlowDownerTheme.colors.surface3)
            if (presets.presets.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.presets_none), color = SlowDownerTheme.colors.fg2) },
                    enabled = false,
                    onClick = {},
                )
            } else {
                presets.presets.forEach { preset ->
                    PresetItem(preset, isActive = preset.id == presets.activePresetId, enabled = presets.enabled, actions = actions) {
                        expanded = false
                    }
                }
            }
            HorizontalDivider(color = SlowDownerTheme.colors.surface3)
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.add_to_playlist)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_playlist_add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    actions.onAddToPlaylist()
                },
            )
        }
    }
}

@Composable
private fun PresetItem(preset: PracticePreset, isActive: Boolean, enabled: Boolean, actions: HeaderActions, close: () -> Unit) {
    val colors = SlowDownerTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                preset.name,
                color = if (isActive) colors.amber else colors.fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(200.dp),
            )
        },
        leadingIcon = {
            if (isActive) {
                Icon(painterResource(Res.drawable.ic_h_check), contentDescription = null, tint = colors.amber, modifier = Modifier.size(16.dp))
            } else {
                Spacer(Modifier.size(16.dp))
            }
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    close()
                    actions.onPresetDelete(preset)
                },
                enabled = enabled,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.preset_delete),
                    tint = colors.fg2,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        enabled = enabled,
        onClick = {
            close()
            actions.onPresetSelected(preset)
        },
    )
}

private const val SEPARATOR = " · "

/** Fill of the Analyze pill while it runs: the prototype's amber + "22". */
private const val RUNNING_FILL_ALPHA = 0.13f

private val RoundedBpm = RoundedCornerShape(4.dp)
