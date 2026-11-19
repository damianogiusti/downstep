package com.damianogiusti.slowdowner.ui.player

import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playback.LoopRegion
import com.damianogiusti.slowdowner.domain.practice.PracticePreset
import com.damianogiusti.slowdowner.domain.practice.PracticeSettings

data class PlayerUiState(
    /** Null for nothing open, or for a track opened outside the library (Spotify). */
    val track: LibraryTrack? = null,
    val controls: PlayerControlsState = PlayerControlsState(),
    val presets: List<PracticePreset> = emptyList(),
    val export: ExportUiState = ExportUiState.Idle,
    /** Null while the waveform is off in Settings, or when the engine cannot outline the file. */
    val waveform: WaveformUiState? = null,
    /** Null while loop Fine is off, or without A/B. */
    val loopZoom: LoopZoomUiState? = null,
    /** A track is being decoded: nothing is open meanwhile, and a long recording takes seconds. */
    val isOpening: Boolean = false,
    /**
     * The open track is being reopened as its other source (original or stems). Everything else stays as it was:
     * the controls cannot act on a closed engine, so they wait.
     */
    val isSwitchingMode: Boolean = false,
) {
    val isExporting get() = export is ExportUiState.Running
    /** Spotify tracks have no library entry and cannot be exported. */
    val canExport get() = controls.isOpen && track != null && export == ExportUiState.Idle && !isSwitchingMode
    val activePresetId: Long? get() = presets.firstOrNull { it.settings.matches(controls.practiceSettings) }?.id
}

data class PlayerControlsState(
    val isOpen: Boolean = false,
    val isPlaying: Boolean = false,
    val position: Float = 0f,
    val durationSeconds: Float = 0f,
    val speedSlider: Int = 0,
    val speedPercent: Float = 100f,
    val pitchSlider: Int = 0,
    val pitchSemitones: Float = 0f,
    /** App-wide preference, not engine state. */
    val isPitchFine: Boolean = false,
    /** App-wide preference, not engine state. */
    val isLoopFine: Boolean = false,
    val isLoopOn: Boolean = false,
    val loopStart: Float = 0f,
    val loopEnd: Float = 1f,
) {
    val practiceSettings get() = PracticeSettings(speedPercent, pitchSemitones, LoopRegion(loopStart, loopEnd, isLoopOn))
    /** A/B are worth drawing: an active loop, or one parked while looping is off. */
    val hasLoopPoints get() = isLoopOn || loopStart > 0f || loopEnd < 1f
}

/**
 * The whole file's waveform: bar levels 0..1, scaled to its loudest bar. Plain classes, not data
 * classes: a new instance means new levels, which is all the UI compares.
 */
class WaveformUiState(val levels: FloatArray)

/**
 * The loop and a little around it, to place A and B precisely. */
class LoopZoomUiState(
    val from: Float,
    val to: Float,
    /** Bar levels across the window, scaled like the whole file's; null draws a plain track. */
    val levels: FloatArray?,
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val fraction: Float) : ExportUiState
    /** The file is rendered: the user picks share or save. */
    data class Ready(val file: ExportedFile) : ExportUiState
}
