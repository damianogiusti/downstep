package com.damianogiusti.slowdowner.ui.player

import androidx.compose.runtime.Immutable
import com.damianogiusti.slowdowner.domain.analysis.SuggestedSection
import com.damianogiusti.slowdowner.domain.click.ClickSettings
import com.damianogiusti.slowdowner.domain.export.ExportScope
import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.loops.ProgressionStatus
import com.damianogiusti.slowdowner.domain.practice.PracticePreset

// The player's state, split by the part of the screen that reads it: each composable collects only
// its own flow, and a change (a pass, a slider move, play/pause) recomposes only what shows it. The
// playhead is not here at all: it is PlayerViewModel.position, read in draw code.

/** Where the open track comes from, for the header's meta line. */
enum class TrackSource { None, Local, YouTube, External }

/** Header: title, meta line and what the right-hand buttons may do. */
@Immutable
data class HeaderState(
    /** The open track, or the one being opened. Null with nothing open (or an external source). */
    val title: String? = null,
    val source: TrackSource = TrackSource.None,
    val durationSeconds: Float = 0f,
    /** The tempo in use: typed by the user, else detected. Null shows "BPM —". */
    val bpm: Float? = null,
    /** The user's own tempo (typed or tapped), which wins over [detectedBpm]. */
    val typedBpm: Float? = null,
    /** Found by the tempo analysis of the file. */
    val detectedBpm: Float? = null,
    /** The tempo analysis of the open track is running (the first time it is opened). */
    val isDetectingTempo: Boolean = false,
    val loopCount: Int = 0,
    val isOpen: Boolean = false,
    /** A track is decoding: controls are frozen until it is open. */
    val isLoading: Boolean = false,
    val canExport: Boolean = false,
    val canEditBpm: Boolean = false,
) {
    /** The tempo shown comes from the analysis, not from the user. */
    val isBpmDetected: Boolean get() = typedBpm == null && detectedBpm != null
}

/** The bookmark menu: presets of the open track. */
@Immutable
data class PresetsState(
    val presets: List<PracticePreset> = emptyList(),
    val activePresetId: Long? = null,
    val enabled: Boolean = false,
)

/**
 * Backends the app may not have (README §6, the seams in domain.analysis / domain.click). Without one,
 * Analyze, Metronome and Count-in show as designed but disabled, with a tooltip. The desktop engine
 * clicks; Analyze has no backend yet.
 */
@Immutable
data class PlayerFeatures(
    val canAnalyze: Boolean = false,
    val hasMetronome: Boolean = false,
    val hasCountIn: Boolean = false,
)

/**
 * Metronome and Count-in toggles and their menu (volume, count-in length, every loop); the toggles are
 * only ever on when [PlayerFeatures] says they exist.
 */
@Immutable
data class ClickUiState(
    val isMetronomeOn: Boolean = false,
    val isCountInOn: Boolean = false,
    /** 0..1. */
    val volume: Float = ClickSettings.DEFAULT_VOLUME,
    val countInBeats: Int = ClickSettings.DEFAULT_COUNT_IN_BEATS,
    val countInEveryLoop: Boolean = false,
)

enum class AnalysisStatus { Idle, Running, Done }

/** Analyze: suggestions stay dashed until the user accepts one (README §4.8). */
@Immutable
data class AnalysisUiState(
    val status: AnalysisStatus = AnalysisStatus.Idle,
    val suggestions: List<SuggestionModel> = emptyList(),
)

/** A suggested loop from Analyze; [id] is only an index within the current suggestions. */
@Immutable
data class SuggestionModel(val id: Int, val name: String, val start: Float, val end: Float, val lengthSeconds: Float) {
    val section: SuggestedSection get() = SuggestedSection(name, start, end)
}

/** One chip of the loop strip (README §2.3). */
@Immutable
data class LoopChipModel(
    val id: Long,
    val name: String,
    /** Index into SlowDownerTheme.colors.loopColors: (id − 1) mod 4, so loop 1 is amber. */
    val colorIndex: Int,
    val lengthSeconds: Float,
    val passes: Int,
    val isActive: Boolean,
)

@Immutable
data class LoopChipsState(
    val chips: List<LoopChipModel> = emptyList(),
    /** False while nothing is open, loading or exporting: chips and "+ New loop" are disabled. */
    val enabled: Boolean = false,
)

/** A loop drawn on the overview waveform. */
@Immutable
data class LoopRegionModel(val id: Long, val start: Float, val end: Float, val colorIndex: Int, val isActive: Boolean)

/** Peak levels 0..1 of [from]..[to] of the file, one per equal slice. Same instance until it changes. */
@Immutable
class Peaks(val from: Float, val to: Float, val values: FloatArray)

/** The full-width overview waveform (README §2.4). */
@Immutable
data class OverviewWaveformState(
    val isOpen: Boolean = false,
    val durationSeconds: Float = 0f,
    /** Null until the outline is read after open. */
    val peaks: Peaks? = null,
    val regions: List<LoopRegionModel> = emptyList(),
    /** The active loop (A/B pills, drag handles, coloured bars); null without one. */
    val active: LoopRegionModel? = null,
    val isLoopOn: Boolean = false,
    /** The zoom strip's window, drawn as a faint bracket. */
    val zoomWindow: LoopBounds? = null,
    val suggestions: List<SuggestionModel> = emptyList(),
    /** False while loading or exporting: no seeks, no drags. */
    val enabled: Boolean = false,
)

/** The zoom strip under the overview (README §2.5): the active loop ± padding. Null without an active loop. */
@Immutable
data class ZoomStripState(
    val loopId: Long,
    val loopName: String,
    val colorIndex: Int,
    val start: Float,
    val end: Float,
    val window: LoopBounds,
    val durationSeconds: Float,
    /** Covers exactly [window]; null until read. */
    val peaks: Peaks?,
    /** Beats and bars in the loop; null without a BPM (then the strip shows the length in seconds). */
    val beats: Float?,
    val enabled: Boolean,
)

/** Transport row, centre and loop pill (README §2.6). */
@Immutable
data class TransportState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoopOn: Boolean = false,
    /** Shown in the loop pill; null = "Loop off" disabled (no active loop). */
    val activeLoopName: String? = null,
    val hasLoops: Boolean = false,
    val enabled: Boolean = false,
)

/** The active-loop tool group. */
@Immutable
data class LoopToolsState(
    val hasActiveLoop: Boolean = false,
    val canHalve: Boolean = false,
    val canDouble: Boolean = false,
    val canNudgeBack: Boolean = false,
    val canNudgeForward: Boolean = false,
    val enabled: Boolean = false,
)

/** The pass pill: "Pass 4 · +3% in 2" (README §2.6). Null without an active loop. */
@Immutable
data class PassState(val passes: Int, val progression: ProgressionStatus)

/** Speed and pitch cards, and the focus-mode read-outs. */
@Immutable
data class SpeedPitchState(
    val speedPercent: Float = 100f,
    val speedSlider: Int = 0,
    val pitchSemitones: Float = 0f,
    val pitchSlider: Int = 0,
    val enabled: Boolean = false,
)

/** The Progression card of the active loop (README §2.8). */
@Immutable
data class ProgressionCardState(
    val loopId: Long,
    val loopName: String,
    val progression: Progression,
    val passes: Int,
    val status: ProgressionStatus,
    /** 0..1 along from → to. */
    val progressFraction: Float,
    val enabled: Boolean,
)

/** The Notes card of the active loop. [notes] is the saved text: the card keeps its own while typing. */
@Immutable
data class NotesCardState(val loopId: Long, val loopName: String, val notes: String)

/** Focus mode and the library drawer: UI-only, never saved. */
@Immutable
data class PlayerLayout(val isFocusMode: Boolean = false, val isDrawerOpen: Boolean = false)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    /**
     * The options dialog is open: the user picks what to export. With no loops, only the full
     * track can be picked.
     */
    data class Choosing(val scope: ExportScope, val loopCount: Int) : ExportUiState {
        val hasLoops: Boolean get() = loopCount > 0
        val fileCount: Int get() = scope.fileCount(loopCount)
    }
    /** One bar and one cancel for the whole batch of [fileCount] files. */
    data class Running(val fraction: Float, val fileCount: Int = 1) : ExportUiState
    /** The files are rendered: the user picks share or save. */
    data class Ready(val files: List<ExportedFile>) : ExportUiState
}
