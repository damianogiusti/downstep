package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Where the running app is, as jpackage lays it out. The app replaces itself only where it can write
 * next to itself: not from the disk image, not from a folder of another user, not from the copy
 * macOS runs in a random place (App Translocation) when an app was never moved after the download.
 */
sealed interface AppInstallation {
    /** `Downstep.app`. */
    data class MacBundle(val bundle: File) : AppInstallation

    /** The `Downstep` folder with `Downstep.exe`, wherever the zip was unpacked. */
    data class WindowsFolder(val folder: File) : AppInstallation

    companion object {
        /** Null when not packaged (a Gradle run) or when the app cannot replace itself. */
        fun current(): AppInstallation? {
            // Set by the jpackage launcher: Downstep.app/Contents/MacOS/Downstep or Downstep\Downstep.exe.
            val launcher = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
            val os = System.getProperty("os.name").orEmpty()
            return when {
                os.startsWith("Mac") -> macBundle(launcher)
                os.startsWith("Windows") -> windowsFolder(launcher)
                else -> null
            }
        }

        internal fun macBundle(launcher: File): MacBundle? {
            val bundle = launcher.parentFile?.parentFile?.parentFile ?: return null
            if (bundle.extension != "app" || "/AppTranslocation/" in bundle.path) return null
            return MacBundle(bundle).takeIf { isWritable(bundle) && isWritable(bundle.parentFile) }
        }

        internal fun windowsFolder(launcher: File): WindowsFolder? {
            // The swap script starts Downstep.exe: a renamed exe stays out of it.
            if (!launcher.name.equals("Downstep.exe", ignoreCase = true)) return null
            val folder = launcher.parentFile ?: return null
            return WindowsFolder(folder).takeIf { isWritable(folder) && isWritable(folder.parentFile) }
        }

        /**
         * On macOS the system call answers, and nothing is written into the signed bundle. On Windows it
         * is not reliable for folders: try for real.
         */
        private fun isWritable(directory: File?): Boolean {
            if (directory == null || !directory.isDirectory) return false
            if (File.separatorChar == '/') return Files.isWritable(directory.toPath())
            return try {
                File.createTempFile(".downstep-", ".tmp", directory).delete()
                true
            } catch (_: IOException) {
                false
            }
        }
    }
}
