package com.damianogiusti.slowdowner.ui.stems

/** What the library shows about the stem separation. Render-ready: percents are whole numbers. */
data class StemSeparationUiState(
    /** Ids of the tracks that have stems: they get the marker and the "Separate again" menu. */
    val separatedTrackIds: Set<String> = emptySet(),
    val progress: StemProgress = StemProgress.None,
    val dialog: StemDialog = StemDialog.None,
)

/** The bottom banner: one job at a time, a download of the model or a separation. */
sealed interface StemProgress {
    data object None : StemProgress
    data class DownloadingModel(val percent: Int) : StemProgress
    data class Separating(val percent: Int) : StemProgress
}

sealed interface StemDialog {
    data object None : StemDialog

    /** First use: what is downloaded, how big and from where. [isDeprecatedPlatform] adds the Intel Mac notice. */
    data class DownloadModel(val isDeprecatedPlatform: Boolean) : StemDialog

    /** The model is there, but this Mac is deprecated for the separation: asked once per launch. */
    data object ConfirmDeprecatedPlatform : StemDialog
}
