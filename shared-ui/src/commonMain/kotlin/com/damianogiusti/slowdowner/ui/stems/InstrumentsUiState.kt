package com.damianogiusti.slowdowner.ui.stems

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.TrackMode
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

/** The Instruments panel of the open track. */
sealed interface InstrumentsUiState {
    /** Nothing is open, or the open track has no stems: the player hides the panel and its action. */
    data object Unavailable : InstrumentsUiState

    data class Ready(
        val trackId: String,
        val mode: TrackMode,
        /** The track is reopening as the other source: the controls wait, the audio is stopped meanwhile. */
        val isSwitching: Boolean,
        /** The stems the track has, in the fixed order of [StemKind]. */
        val stems: List<InstrumentRow>,
    ) : InstrumentsUiState {
        /** In the full-track mode the stems are not played: their controls show but do nothing. */
        val areStemControlsEnabled: Boolean get() = mode == TrackMode.STEMS && !isSwitching
    }
}

/** [gain] is what plays (0..1): the dragged value until it is saved, then the saved one. */
data class InstrumentRow(val kind: StemKind, val name: StringResource, val gain: Float) {
    val percent: Int get() = (gain * PERCENT).roundToInt()
    val isMuted: Boolean get() = percent == 0

    private companion object {
        const val PERCENT = 100
    }
}
