package com.damianogiusti.slowdowner.ui.update

sealed interface UpdateUiState {
    data object None : UpdateUiState

    /**
     * A newer version exists: the banner shows as long as this lasts. [isDialogOpen]: the dialog is on
     * screen, never at the same time as the support prompt. [canInstall]: false means the app
     * cannot replace itself here, and the dialog links [pageUrl] instead.
     */
    data class Available(
        val version: String,
        val installedVersion: String,
        val pageUrl: String,
        val canInstall: Boolean,
        val isDialogOpen: Boolean,
        val install: UpdateInstallState,
    ) : UpdateUiState
}

sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    /** [percent] reaches 100 while the new app is unpacked and checked. */
    data class Downloading(val percent: Int) : UpdateInstallState
    data object NetworkError : UpdateInstallState
    /** The update did not work: the dialog offers the download page. */
    data object Failed : UpdateInstallState
    /** The app quits now, and the new one opens. */
    data object Restarting : UpdateInstallState
}
