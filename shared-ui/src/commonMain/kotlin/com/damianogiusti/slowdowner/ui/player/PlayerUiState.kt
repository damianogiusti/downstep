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
) {
    val isExporting get() = export is ExportUiState.Running
    /** Spotify tracks have no library entry and cannot be exported. */
    val canExport get() = controls.isOpen && track != null && export == ExportUiState.Idle
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
    val isLoopOn: Boolean = false,
    val loopStart: Float = 0f,
    val loopEnd: Float = 1f,
) {
    val practiceSettings get() = PracticeSettings(speedPercent, pitchSemitones, LoopRegion(loopStart, loopEnd, isLoopOn))
    /** A/B are worth drawing: an active loop, or one parked while looping is off. */
    val hasLoopPoints get() = isLoopOn || loopStart > 0f || loopEnd < 1f
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Running(val fraction: Float) : ExportUiState
    /** The file is rendered: the user picks share or save. */
    data class Ready(val file: ExportedFile) : ExportUiState
}
