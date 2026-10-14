package com.damianogiusti.slowdowner.data.library

import java.io.File
import java.io.InputStream

/**
 * On desktop a "document" is a plain file the user picked: the content URI is its absolute path.
 */
class FileDocumentDataSource : DocumentDataSource {

    override fun displayName(contentUri: String): String? = File(contentUri).name.takeIf { it.isNotEmpty() }

    override fun copy(contentUri: String, target: File) {
        File(contentUri).copyTo(target, overwrite = true)
    }

    override fun open(contentUri: String): InputStream = File(contentUri).inputStream()

    override fun write(source: File, contentUri: String) {
        source.copyTo(File(contentUri), overwrite = true)
    }
}
