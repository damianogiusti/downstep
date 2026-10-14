package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.library.DocumentDataSource
import java.io.File
import java.io.InputStream

/** Treats a content URI as a plain file path. */
class FakeFileDocumentDataSource : DocumentDataSource {
    override fun displayName(contentUri: String): String = File(contentUri).name

    override fun copy(contentUri: String, target: File) {
        File(contentUri).copyTo(target, overwrite = true)
    }

    override fun open(contentUri: String): InputStream = File(contentUri).inputStream()

    override fun write(source: File, contentUri: String) {
        source.copyTo(File(contentUri), overwrite = true)
    }
}
