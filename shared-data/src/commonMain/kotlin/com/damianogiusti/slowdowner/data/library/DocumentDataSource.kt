package com.damianogiusti.slowdowner.data.library

import java.io.File
import java.io.InputStream

/** Documents the user picks through the system file picker. */
interface DocumentDataSource {
    fun displayName(contentUri: String): String?
    fun copy(contentUri: String, target: File)
    /** For reading a document as a stream, without a local copy. The caller closes it. */
    fun open(contentUri: String): InputStream
    /** Writes [source] into a document the user created with the picker. */
    fun write(source: File, contentUri: String)
}
