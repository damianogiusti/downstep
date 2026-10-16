package com.damianogiusti.slowdowner.ui.player.components

import com.damianogiusti.slowdowner.ui.resources.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.damianogiusti.slowdowner.ui.common.CountBadge
import com.damianogiusti.slowdowner.ui.common.IconPill
import com.damianogiusti.slowdowner.ui.common.Pill
import com.damianogiusti.slowdowner.ui.common.PillToggle
import com.damianogiusti.slowdowner.ui.common.SegmentedPill
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// PLACEHOLDER (group H): final signature, minimal body. See ui-contract.md, README §2.2.

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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = DownstepDimens.gutter, end = DownstepDimens.gutter, top = 18.dp, bottom = 6.dp),
    ) {
        Pill(onClick = actions.onOpenLibrary, tooltip = stringResource(Res.string.library_open_description)) {
            Text(stringResource(Res.string.library))
            CountBadge(trackCount.toString())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.title ?: stringResource(if (state.isOpen) Res.string.external_track else Res.string.no_track),
                style = SlowDownerTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val meta = buildList {
                    when (state.source) {
                        TrackSource.Local -> add(stringResource(Res.string.source_local))
                        TrackSource.YouTube -> add(stringResource(Res.string.source_youtube))
                        else -> Unit
                    }
                    if (state.durationSeconds > 0f) add(formatClock(state.durationSeconds))
                }.joinToString(" · ")
                Text(meta, style = SlowDownerTheme.type.meta, color = SlowDownerTheme.colors.fg2)
                Text(
                    state.bpm?.let { stringResource(Res.string.header_bpm, it.roundToInt().toString()) } ?: stringResource(Res.string.header_bpm_unset),
                    style = SlowDownerTheme.type.meta,
                    color = SlowDownerTheme.colors.fg2,
                    modifier = Modifier.clickable(enabled = state.canEditBpm, onClick = actions.onEditBpm),
                )
                Text(
                    if (state.loopCount == 0) stringResource(Res.string.loops_none) else pluralStringResource(Res.plurals.loops_count, state.loopCount, state.loopCount),
                    style = SlowDownerTheme.type.meta,
                    color = SlowDownerTheme.colors.fg2,
                )
            }
        }
        Pill(
            onClick = actions.onAnalyze,
            enabled = features.canAnalyze && state.isOpen,
            tooltip = stringResource(if (features.canAnalyze) Res.string.analyze_tooltip else Res.string.analyze_unavailable),
        ) {
            Text(stringResource(if (analysis == AnalysisStatus.Running) Res.string.analyze_running else Res.string.analyze))
        }
        SegmentedPill {
            PillToggle(
                checked = clicks.isMetronomeOn,
                onClick = actions.onMetronomeToggled,
                text = stringResource(Res.string.metronome),
                enabled = features.hasMetronome,
                tooltip = if (features.hasMetronome) null else stringResource(Res.string.metronome_unavailable),
            )
            PillToggle(
                checked = clicks.isCountInOn,
                onClick = actions.onCountInToggled,
                text = stringResource(Res.string.count_in),
                enabled = features.hasCountIn,
                tooltip = if (features.hasCountIn) null else stringResource(Res.string.count_in_unavailable),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconPill(painterResource(Res.drawable.ic_tune), stringResource(Res.string.eq_title), actions.onShowEffects)
            IconPill(painterResource(Res.drawable.ic_bookmark), stringResource(Res.string.preset_save_title), actions.onSavePreset, enabled = presets.enabled)
            IconPill(painterResource(Res.drawable.ic_share), stringResource(Res.string.export), actions.onExport, enabled = state.canExport)
            IconPill(painterResource(Res.drawable.ic_graphic_eq), stringResource(Res.string.theme_toggle), actions.onThemeToggled)
            IconPill(painterResource(Res.drawable.ic_expand_more), stringResource(Res.string.focus_mode), actions.onFocusToggled)
        }
    }
}
