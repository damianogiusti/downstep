package com.damianogiusti.slowdowner.data.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream

class ContentResolverDocumentDataSource(
    private val contentResolver: ContentResolver,
) : DocumentDataSource {

    override fun displayName(contentUri: String): String? =
        contentResolver.query(Uri.parse(contentUri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    override fun copy(contentUri: String, target: File) {
        open(contentUri).use { source -> target.outputStream().use { source.copyTo(it) } }
    }

    override fun open(contentUri: String): InputStream =
        contentResolver.openInputStream(Uri.parse(contentUri)) ?: throw IOException("Cannot open $contentUri")

    override fun write(source: File, contentUri: String) {
        // "wt": truncate, in case the user picked an existing, longer file.
        val output = contentResolver.openOutputStream(Uri.parse(contentUri), "wt")
            ?: throw IOException("Cannot open $contentUri")
        output.use { target -> source.inputStream().use { it.copyTo(target) } }
    }
}
