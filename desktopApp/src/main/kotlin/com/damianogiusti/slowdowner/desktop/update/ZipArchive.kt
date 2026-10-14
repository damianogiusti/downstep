package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** The Windows release is a zip: the `Downstep` folder, plus the licenses beside it. */
internal object ZipArchive {

    /** Unpacks the entries under [folder]/ into [target]. Throws if there are none. */
    fun extractFolder(zip: File, folder: String, target: File) {
        val root = target.canonicalFile
        var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                // Compress-Archive writes backslashes on older PowerShell versions.
                val name = entry.name.replace('\\', '/')
                if (!name.startsWith("$folder/")) continue
                val file = File(root, name.removePrefix("$folder/")).canonicalFile
                // An entry like ../../x must never land outside the target.
                if (!file.path.startsWith(root.path + File.separator) && file != root) throw IOException("Bad entry in the zip")
                if (entry.isDirectory || name.endsWith("/")) {
                    file.mkdirs()
                } else {
                    file.parentFile.mkdirs()
                    file.outputStream().use { input.copyTo(it) }
                    count++
                }
            }
        }
        if (count == 0) throw IOException("No $folder folder in the zip")
    }
}
