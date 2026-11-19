package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps rendered tracks in memory; [addResult] replaces a successful add. */
class FakeLibraryRepository : LibraryRepository {
    val tracks = MutableStateFlow<List<LibraryTrack>>(emptyList())
    var addResult: ImportResult? = null

    override fun observeTracks(): Flow<List<LibraryTrack>> = tracks
    override suspend fun remove(id: String) { tracks.value = tracks.value.filterNot { it.id == id } }
    override fun findImportableUrl(text: String?): String? = null
    override suspend fun importFromUrl(url: String, onProgress: (ImportProgress) -> Unit) = error("unused")
    override suspend fun importDocument(contentUri: String) = error("unused")

    override suspend fun addRenderedTrack(file: ExportedFile, title: String): ImportResult {
        addResult?.let { return it }
        val track = LibraryTrack("local_${tracks.value.size + 1}", title, file.path, null, 0)
        tracks.value = tracks.value + track
        return ImportResult.Success(track)
    }
}
