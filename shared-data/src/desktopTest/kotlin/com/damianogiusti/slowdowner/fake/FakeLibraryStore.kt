package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.library.LibraryStore
import com.damianogiusti.slowdowner.data.library.LibraryTrackDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeLibraryStore(private val dir: File, tracks: List<LibraryTrackDto> = emptyList()) : LibraryStore {
    val tracks = MutableStateFlow(tracks)

    override fun observe(): Flow<List<LibraryTrackDto>> = tracks
    override suspend fun find(id: String) = tracks.value.firstOrNull { it.id == id }
    override suspend fun add(track: LibraryTrackDto) {
        tracks.value = tracks.value.filterNot { it.id == track.id } + track
    }
    override suspend fun remove(id: String) {
        tracks.value = tracks.value.filterNot { it.id == id }
    }
    override fun fileFor(id: String, extension: String) = File(dir, "$id.$extension")
}
