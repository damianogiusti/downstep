package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.logging.Logger
import java.io.File
import java.io.IOException

/**
 * CHANGELOG.txt in the app's resources folder: desktopApp copies it there at build time (the same
 * folder as the licenses). [resourcesDir] is null outside a packaged or Gradle-run app.
 */
class ResourceChangelogDataSource(
    private val resourcesDir: File?,
    private val logger: Logger,
) : ChangelogDataSource {

    override fun read(): String? {
        val file = File(resourcesDir ?: return null, FILE_NAME)
        if (!file.isFile) return null
        return try {
            file.readText()
        } catch (e: IOException) {
            logger.w("Cannot read the changelog", e)
            null
        }
    }

    private companion object {
        const val FILE_NAME = "CHANGELOG.txt"
    }
}
