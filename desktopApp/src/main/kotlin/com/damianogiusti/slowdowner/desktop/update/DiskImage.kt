package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.desktop.update.DesktopUpdateInstaller.Companion.run
import com.damianogiusti.slowdowner.logging.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** The Mac release is a disk image, as people download it from the site. */
internal object DiskImage {

    /** Copies the one app in [dmg] to [target]. The image is mounted out of sight and always detached. */
    fun copyApp(dmg: File, target: File, logger: Logger) {
        val mountPoint = Files.createTempDirectory("downstep-update").toFile()
        try {
            run("hdiutil", "attach", "-nobrowse", "-noautoopen", "-readonly", "-mountpoint", mountPoint.path, dmg.path)
            try {
                val app = mountPoint.listFiles { file -> file.extension == "app" && !Files.isSymbolicLink(file.toPath()) }
                    ?.singleOrNull() ?: throw IOException("No app in the disk image")
                target.parentFile.mkdirs()
                // ditto keeps what a plain copy can lose in a bundle: links, permissions, signatures.
                run("ditto", app.path, target.path)
            } finally {
                // The copy is done: a mounted image left behind only shows in Disk Utility.
                runCatching { run("hdiutil", "detach", mountPoint.path, "-force") }
                    .onFailure { logger.w("Cannot detach the update disk image", it) }
            }
        } finally {
            mountPoint.delete()
        }
    }
}
