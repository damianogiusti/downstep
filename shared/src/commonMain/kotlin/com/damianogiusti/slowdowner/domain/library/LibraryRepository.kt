package com.damianogiusti.slowdowner.domain.library

import com.damianogiusti.slowdowner.domain.export.ExportedFile
import kotlinx.coroutines.flow.Flow

/** Every import ends as a file on disk: the engine can only play local files. */
interface LibraryRepository {
    fun observeTracks(): Flow<List<LibraryTrack>>

    suspend fun remove(id: String)

    /** Returns the importable URL contained in [text], or null if there is none. */
    fun findImportableUrl(text: String?): String?

    suspend fun importFromUrl(url: String, onProgress: (ImportProgress) -> Unit): ImportResult

    /** [contentUri] is a document URI picked by the user; its content is copied. */
    suspend fun importDocument(contentUri: String): ImportResult

    /** Moves [file], rendered by the app, into the library as a new track called [title]. */
    suspend fun addRenderedTrack(file: ExportedFile, title: String): ImportResult
}
