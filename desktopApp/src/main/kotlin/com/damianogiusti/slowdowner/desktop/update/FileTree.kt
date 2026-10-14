package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes a folder and what is in it. Unlike Kotlin's deleteRecursively, it never follows a link:
 * a link inside goes, what it points to stays. An app bundle or a disk image can hold a link to
 * /Applications, and following it would delete the user's apps.
 */
internal fun File.deleteTree() {
    val root = toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                // A Windows junction looks like a folder: remove the junction, never what it points to.
                if (!attrs.isOther) return FileVisitResult.CONTINUE
                Files.delete(dir)
                return FileVisitResult.SKIP_SUBTREE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        },
    )
}
