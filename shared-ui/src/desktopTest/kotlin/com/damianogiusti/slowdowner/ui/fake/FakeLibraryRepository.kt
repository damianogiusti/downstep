package com.damianogiusti.slowdowner.ui.fake

import com.damianogiusti.slowdowner.domain.export.ExportedFile
import com.damianogiusti.slowdowner.domain.library.ImportProgress
import com.damianogiusti.slowdowner.domain.library.ImportResult
import com.damianogiusti.slowdowner.domain.library.LibraryRepository
import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Accepts every rendered track as "local_new". */
class FakeLibraryRepository : LibraryRepository {
    override fun observeTracks(): Flow<List<LibraryTrack>> = emptyFlow()
    override suspend fun remove(id: String) = Unit
    override fun findImportableUrl(text: String?): String? = null
    override suspend fun importFromUrl(url: String, onProgress: (ImportProgress) -> Unit) = ImportResult.Unavailable
    override suspend fun importDocument(contentUri: String) = ImportResult.Unavailable
    override suspend fun addRenderedTrack(file: ExportedFile, title: String) =
        ImportResult.Success(LibraryTrack("local_new", title, "/library/local_new.mp3", null, 0))
}
