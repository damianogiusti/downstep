package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.navigation.ActivityActions
import com.damianogiusti.slowdowner.ui.navigation.ActivityViewModels
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * The desktop side of [ActivityActions]: native macOS file dialogs instead of the Android pickers,
 * and "show in Finder" where Android opens the share sheet.
 */
class DesktopActions(
    private val window: Frame,
    private val viewModels: ActivityViewModels,
    private val logger: Logger,
) {

    fun toActivityActions() = ActivityActions(
        onPickFile = { pickFile(AUDIO_EXTENSIONS)?.let { viewModels.imports.onDocumentPicked(it.path) } },
        onOpenSpotify = null,
        onPlayStop = viewModels.player::onPlayStopClicked,
        // No foreground service on desktop: the process plays while the window is open.
        onPlaybackStarted = {},
        onShareExport = { reveal(File(it.path)) },
        onSaveExport = { file -> saveAs(file.name)?.let { viewModels.player.onExportSaveTargetPicked(it.path) } },
        onPickBackup = { pickFile(setOf(BACKUP_EXTENSION, "zip"))?.let { viewModels.backup.onRestorePicked(it.path) } },
        onShareBackup = { reveal(File(it.path)) },
        onSaveBackup = { file -> saveAs(file.name)?.let { viewModels.backup.onSaveTargetPicked(it.path) } },
    )

    private fun pickFile(extensions: Set<String>): File? {
        val dialog = FileDialog(window, null, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
            isVisible = true
        }
        return dialog.selectedFile()
    }

    private fun saveAs(suggestedName: String): File? {
        val dialog = FileDialog(window, null, FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        return dialog.selectedFile()
    }

    private fun reveal(file: File) {
        // A failure here only means the Finder does not open: the file is already written.
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file)
            else desktop.open(file.parentFile)
        }.onFailure { logger.w("Cannot show the file in the Finder", it) }
    }

    private fun FileDialog.selectedFile(): File? {
        val name = file ?: return null
        return File(directory, name)
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "wav", "aac", "flac", "aif", "aiff", "caf", "mp4")
        const val BACKUP_EXTENSION = "sldbackup"
    }
}
