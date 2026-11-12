package com.damianogiusti.slowdowner.domain.backup

import com.damianogiusti.slowdowner.domain.library.LibraryTrack
import com.damianogiusti.slowdowner.domain.playlists.Playlist
import com.damianogiusti.slowdowner.fake.FakeBackupRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDateTime

class BackupUseCasesTest {

    private val repository = FakeBackupRepository()
    private val now = { LocalDateTime(2026, 10, 13, 9, 5, 7) }

    @Test
    fun `when a track is exported then its id and title name the backup`() = runTest {
        val track = LibraryTrack("yt_abc", "Autumn Leaves", "/a.m4a", null, 0)

        ExportTrackBackupUseCase(repository, now)(track) {}

        assertEquals(BackupScope.Track("yt_abc"), repository.lastScope)
        assertEquals("Downstep-Autumn-Leaves-20261013090507.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when the same track is exported twice a second apart then the backups have different names`() = runTest {
        val track = LibraryTrack("yt_abc", "Autumn Leaves", "/a.m4a", null, 0)
        val times = ArrayDeque(listOf(LocalDateTime(2026, 10, 13, 23, 59, 59), LocalDateTime(2026, 10, 14, 0, 0, 0)))
        val export = ExportTrackBackupUseCase(repository) { times.removeFirst() }

        export(track) {}
        val first = repository.lastFileName
        export(track) {}

        assertEquals("Downstep-Autumn-Leaves-20261013235959.sldbackup", first)
        assertEquals("Downstep-Autumn-Leaves-20261014000000.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when the library is exported then the scope is the whole library`() = runTest {
        val exportLibrary = ExportLibraryBackupUseCase(repository, now)

        exportLibrary {}

        assertEquals(BackupScope.Library, repository.lastScope)
        assertEquals("Downstep-Libreria-20261013090507.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when a playlist is exported then its id and name are used`() = runTest {
        ExportPlaylistBackupUseCase(repository, now)(Playlist(7, "Jazz", 3)) {}

        assertEquals(BackupScope.Playlist(7), repository.lastScope)
        assertEquals("Downstep-Jazz-20261013090507.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when the title has unsafe characters then the file name replaces them`() = runTest {
        val track = LibraryTrack("local_1", "AC/DC: Back?", "/a.mp3", null, 0)

        ExportTrackBackupUseCase(repository, now)(track) {}

        assertEquals("Downstep-AC-DC-Back-20261013090507.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when the title has repeated or outer spaces then the file name has single dashes`() = runTest {
        val track = LibraryTrack("local_2", "  Take   Five\t", "/a.mp3", null, 0)

        ExportTrackBackupUseCase(repository, now)(track) {}

        assertEquals("Downstep-Take-Five-20261013090507.sldbackup", repository.lastFileName)
    }

    @Test
    fun `when a backup is restored then the choice reaches the repository and its result is returned`() = runTest {
        repository.restoreResult = RestoreResult.Success(tracksAdded = 2, tracksSkipped = 1, playlistsRestored = 1)

        val result = RestoreBackupUseCase(repository)("content://backup", PlaylistConflictChoice.Overwrite) {}

        assertEquals("content://backup", repository.lastRestoredUri)
        assertEquals(PlaylistConflictChoice.Overwrite, repository.lastConflictChoice)
        assertEquals(RestoreResult.Success(2, 1, 1), result)
    }

    @Test
    fun `when looking for conflicts then the repository names are returned`() = runTest {
        repository.playlistConflicts = listOf("Jazz")

        assertEquals(listOf("Jazz"), FindPlaylistConflictsUseCase(repository)("content://backup"))
    }

    @Test
    fun `when a backup is saved then the repository copies it`() = runTest {
        assertTrue(SaveBackupUseCase(repository)(BackupFile("/cache/b.sldbackup", "b.sldbackup"), "content://target"))
    }
}
