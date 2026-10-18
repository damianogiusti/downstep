package com.damianogiusti.slowdowner.ui.player

import androidx.compose.runtime.Stable
import com.damianogiusti.slowdowner.domain.effects.IsolationBands
import com.damianogiusti.slowdowner.domain.loops.LoopBounds
import com.damianogiusti.slowdowner.domain.loops.Progression
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.theme.ThemeSelection

// Everything the player's components can trigger, one class per component group. They are built once
// per screen (remember(viewModel) in PlayerScreen) and hold only stable function references, so a
// component that receives them still skips when its state has not changed.

/** Header (README §2.2). Dialogs and sheets it opens are owned by PlayerScreen. */
@Stable
class HeaderActions(
    val onOpenLibrary: () -> Unit,
    /** Click on "BPM …" in the meta line: PlayerScreen shows the BPM dialog. */
    val onEditBpm: () -> Unit,
    val onAnalyze: () -> Unit,
    val onMetronomeToggled: () -> Unit,
    val onCountInToggled: () -> Unit,
    /** The window's fullscreen on or off. Null where there is none (Android): the header shows no toggle. */
    val onFullscreenToggled: (() -> Unit)?,
    /** The theme menu: hover or focus on an item shows it, leaving goes back, a click saves (ThemeViewModel). */
    val onThemePreview: (ThemeSelection) -> Unit,
    val onThemePreviewEnd: () -> Unit,
    val onThemeApplied: (ThemeSelection) -> Unit,
    /** "Save preset…" in the bookmark menu: PlayerScreen shows the name dialog. */
    val onSavePreset: () -> Unit,
    val onPresetSelected: (PracticePreset) -> Unit,
    /** PlayerScreen asks for confirmation first. */
    val onPresetDelete: (PracticePreset) -> Unit,
    val onExport: () -> Unit,
    /** The Equalizer (Effects) sheet: the design has no entry for it, the header keeps one. */
    val onShowEffects: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    /** The click menu beside the Metronome / Count-in toggles: volume 0..1 (at the end of a drag). */
    val onClickVolumeChanged: (volume: Float) -> Unit = {},
    val onCountInBeatsChanged: (beats: Int) -> Unit = {},
    val onCountInEveryLoopToggled: () -> Unit = {},
)

/** Loop chips strip (README §2.3). */
@Stable
class LoopChipActions(
    val onSelect: (loopId: Long) -> Unit,
    val onNewLoop: () -> Unit,
    val onRename: (loopId: Long, name: String) -> Unit,
    val onDelete: (loopId: Long) -> Unit,
    val onSuggestionAccepted: (suggestionId: Int) -> Unit,
    val onSuggestionDismissed: (suggestionId: Int) -> Unit,
)

/** Overview waveform and zoom strip (README §2.4, §2.5). */
@Stable
class WaveformActions(
    /** A click: seek there (0..1 of the file). */
    val onSeek: (position: Float) -> Unit,
    /** The end of an A/B or top-bar drag, once: the bounds shown at release (free, never rounded to a beat). */
    val onLoopBoundsChanged: (loopId: Long, bounds: LoopBounds) -> Unit,
    /** A click on the overview off any loop's top bar: seek, turning the loop off outside it. */
    val onOverviewClick: (position: Float) -> Unit = onSeek,
    /** A click on a loop's top bar on the overview: "play this loop". */
    val onLoopBarClick: (loopId: Long, position: Float) -> Unit = { _, _ -> },
    /** A drag on the overview picked up a loop that is not the active one. */
    val onLoopGrabbed: (loopId: Long) -> Unit = {},
)

/** Transport row centre and loop pill (README §2.6). */
@Stable
class TransportActions(
    val onPlayPause: () -> Unit,
    val onToStart: () -> Unit,
    /** −5 / +5 seconds. */
    val onSeekBy: (seconds: Float) -> Unit,
    val onNextLoop: () -> Unit,
    val onLoopToggled: () -> Unit,
)

/** Active-loop tool group. */
@Stable
class LoopToolActions(
    /** −1 earlier, +1 later. */
    val onNudge: (direction: Int) -> Unit,
    val onHalve: () -> Unit,
    val onDouble: () -> Unit,
    val onDelete: () -> Unit,
)

/** Speed and pitch cards. Sliders use the engine's slider positions (0..SLIDER_MAX). */
@Stable
class SpeedPitchActions(
    val onSpeedSlider: (position: Int) -> Unit,
    /** ±1 step of 5%. */
    val onSpeedStep: (steps: Int) -> Unit,
    val onSpeedReset: () -> Unit,
    val onPitchSlider: (position: Int) -> Unit,
    /** ±1 semitone. */
    val onPitchStep: (steps: Int) -> Unit,
    val onPitchReset: () -> Unit,
)

/** Isolate card: the EffectsViewModel's band overlay. */
@Stable
class IsolateActions(val onIsolationChanged: (IsolationBands) -> Unit)

/** Progression card: the switch and the steppers send the whole edited progression. */
@Stable
class ProgressionActions(val onProgressionChanged: (loopId: Long, progression: Progression) -> Unit)

/** Notes card: every edit; the ViewModel saves after a pause. */
@Stable
class NotesActions(val onNotesChanged: (loopId: Long, notes: String) -> Unit)

/** Built once per PlayerScreen. */
@Stable
class PlayerActions(
    val header: HeaderActions,
    val chips: LoopChipActions,
    val waveform: WaveformActions,
    val transport: TransportActions,
    val loopTools: LoopToolActions,
    val speedPitch: SpeedPitchActions,
    val isolate: IsolateActions,
    val progression: ProgressionActions,
    val notes: NotesActions,
    val onCloseDrawer: () -> Unit,
)
