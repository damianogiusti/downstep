package com.damianogiusti.slowdowner.data.backup

import com.damianogiusti.slowdowner.data.library.LegacyJsonLibraryIndex
import com.damianogiusti.slowdowner.data.library.RoomLibraryStore
import com.damianogiusti.slowdowner.data.library.TrackEntity
import com.damianogiusti.slowdowner.data.practice.PracticePresetEntity
import com.damianogiusti.slowdowner.data.practice.PracticeSettingsColumns
import com.damianogiusti.slowdowner.data.practice.PracticeStateEntity
import com.damianogiusti.slowdowner.domain.backup.BackupExportResult
import com.damianogiusti.slowdowner.domain.backup.BackupFile
import com.damianogiusti.slowdowner.domain.backup.BackupProgress
import com.damianogiusti.slowdowner.domain.backup.BackupScope
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice.Copy
import com.damianogiusti.slowdowner.domain.backup.PlaylistConflictChoice.Overwrite
import com.damianogiusti.slowdowner.domain.backup.RestoreResult
import com.damianogiusti.slowdowner.fake.FakeBackupDao
import com.damianogiusti.slowdowner.fake.FakeFileDocumentDataSource
import com.damianogiusti.slowdowner.fake.FakeTrackDao
import com.damianogiusti.slowdowner.fake.FakeTransactionRunner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBackupRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** The phone that exports. */
    private val source by lazy { Library("source") }
    /** The phone that restores. */
    private val target by lazy { Library("target") }

    private val slow = PracticeSettingsColumns(75f, -2f, 0.2f, 0.4f, isLoopOn = true)
    private val fast = PracticeSettingsColumns(120f, 1f, 0f, 1f, isLoopOn = false)

    @Test
    fun `when a track is exported then the backup holds its audio, state and presets`() = runTest {
        source.addTrack("yt_abc", "m4a", byteArrayOf(1, 2, 3))
        source.dao.states += PracticeStateEntity("yt_abc", slow, 1)
        source.dao.addPreset(PracticePresetEntity(trackId = "yt_abc", name = "Solo", settings = fast, createdAt = 1))

        val result = source.repository.export(BackupScope.Track("yt_abc"), "b.sldbackup") {}

        val file = File((result as BackupExportResult.Success).file.path)
        val manifest = readManifest(file)
        assertEquals(BackupKindDto.Track, manifest.kind)
        assertEquals(NOW, manifest.createdAt)
        assertEquals(listOf("yt_abc"), manifest.tracks.map { it.id })
        assertEquals(BackupManifestMapper.toDto(slow), manifest.tracks.single().practiceState)
        assertEquals(listOf("Solo"), manifest.tracks.single().presets.map { it.name })
        assertEquals(emptyList<BackupPlaylistDto>(), manifest.playlists)
        ZipFile(file).use { zip ->
            assertArrayEquals(byteArrayOf(1, 2, 3), zip.getInputStream(zip.getEntry("audio/yt_abc.m4a")).readBytes())
        }
    }

    @Test
    fun `when the track is gone or its file is missing then there is nothing to export`() = runTest {
        source.dao.tracks += TrackEntity("local_1", "Gone", File(source.libraryDir, "local_1.mp3").path, null, 0)

        assertEquals(BackupExportResult.NothingToExport, source.repository.export(BackupScope.Track("local_1"), "b") {})
        assertEquals(BackupExportResult.NothingToExport, source.repository.export(BackupScope.Track("nope"), "b") {})
        assertEquals(BackupExportResult.NothingToExport, source.repository.export(BackupScope.Playlist(9), "b") {})
    }

    @Test
    fun `when the library is exported then playlists keep their order without missing files`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        source.dao.tracks += TrackEntity("c", "No file", File(source.libraryDir, "c.mp3").path, null, 3)
        source.dao.addPlaylist("Jazz", listOf("b", "c", "a"))
        val progress = mutableListOf<BackupProgress>()

        val result = source.repository.export(BackupScope.Library, "b.sldbackup") { progress += it }

        val manifest = readManifest(File((result as BackupExportResult.Success).file.path))
        assertEquals(listOf("a", "b"), manifest.tracks.map { it.id })
        assertEquals(listOf(BackupPlaylistDto("Jazz", listOf("b", "a"))), manifest.playlists)
        assertEquals(listOf(BackupProgress(0, 2), BackupProgress(1, 2), BackupProgress(2, 2)), progress)
    }

    @Test
    fun `when a playlist is exported then only its tracks are included`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        val playlistId = source.dao.addPlaylist("Jazz", listOf("b"))

        val result = source.repository.export(BackupScope.Playlist(playlistId), "b.sldbackup") {}

        val manifest = readManifest(File((result as BackupExportResult.Success).file.path))
        assertEquals(BackupKindDto.Playlist, manifest.kind)
        assertEquals(listOf("b"), manifest.tracks.map { it.id })
        assertEquals(listOf(BackupPlaylistDto("Jazz", listOf("b"))), manifest.playlists)
    }

    @Test
    fun `when exporting then older backups are deleted`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.repository.export(BackupScope.Track("a"), "old.sldbackup") {}

        source.repository.export(BackupScope.Track("a"), "new.sldbackup") {}

        assertEquals(listOf("new.sldbackup"), source.backupDir.list()?.toList())
    }

    @Test
    fun `when a backup is saved then the file is copied to the document`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        val backup = (source.repository.export(BackupScope.Track("a"), "b.sldbackup") {} as BackupExportResult.Success).file
        val document = File(temp.root, "picked.sldbackup")

        assertTrue(source.repository.saveTo(backup, document.path))
        assertArrayEquals(File(backup.path).readBytes(), document.readBytes())
        assertFalse(source.repository.saveTo(BackupFile("/missing", "missing"), document.path))
    }

    @Test
    fun `when a backup is restored into an empty library then tracks, settings and playlists are added`() = runTest {
        source.addTrack("yt_abc", "m4a", byteArrayOf(1, 2, 3), title = "Autumn Leaves")
        source.addTrack("local_1", "mp3", byteArrayOf(4))
        source.dao.states += PracticeStateEntity("yt_abc", slow, 1)
        source.dao.addPreset(PracticePresetEntity(trackId = "yt_abc", name = "Solo", settings = fast, createdAt = 1))
        source.dao.addPlaylist("Jazz", listOf("local_1", "yt_abc"))
        val backup = exportLibrary()
        val progress = mutableListOf<BackupProgress>()

        val result = target.repository.restore(backup.path, Copy) { progress += it }

        assertEquals(RestoreResult.Success(tracksAdded = 2, tracksSkipped = 0, playlistsRestored = 1), result)
        val restored = target.dao.tracks.single { it.id == "yt_abc" }
        assertEquals("Autumn Leaves", restored.title)
        assertEquals(File(target.libraryDir, "yt_abc.m4a").path, restored.filePath)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(restored.filePath).readBytes())
        assertEquals(slow, target.dao.findState("yt_abc")?.settings)
        assertEquals(listOf("Solo" to fast), target.dao.presets.map { it.name to it.settings })
        val playlist = target.dao.playlists.single()
        assertEquals("Jazz", playlist.name)
        assertEquals(listOf("local_1", "yt_abc"), target.dao.trackIdsOf(playlist.id))
        assertEquals(listOf(BackupProgress(0, 2), BackupProgress(1, 2), BackupProgress(2, 2)), progress)
        // No staging file left behind.
        assertEquals(listOf("local_1.mp3", "yt_abc.m4a"), target.libraryDir.list()?.sorted())
    }

    @Test
    fun `when a track already exists then its row, file, state and same-name preset are kept`() = runTest {
        source.addTrack("yt_abc", "m4a", byteArrayOf(1, 2, 3), title = "Theirs")
        source.dao.states += PracticeStateEntity("yt_abc", fast, 1)
        source.dao.addPreset(PracticePresetEntity(trackId = "yt_abc", name = "Solo", settings = fast, createdAt = 1))
        source.dao.addPreset(PracticePresetEntity(trackId = "yt_abc", name = "Intro", settings = fast, createdAt = 2))
        val backup = exportLibrary()
        val mine = target.addTrack("yt_abc", "m4a", byteArrayOf(9), title = "Mine")
        target.dao.states += PracticeStateEntity("yt_abc", slow, 1)
        target.dao.addPreset(PracticePresetEntity(trackId = "yt_abc", name = "Solo", settings = slow, createdAt = 1))

        val result = target.repository.restore(backup.path, Copy) {}

        assertEquals(RestoreResult.Success(tracksAdded = 0, tracksSkipped = 1, playlistsRestored = 0), result)
        assertEquals(listOf(mine), target.dao.tracks)
        assertArrayEquals(byteArrayOf(9), File(mine.filePath).readBytes())
        assertEquals(slow, target.dao.findState("yt_abc")?.settings)
        assertEquals(listOf("Solo" to slow, "Intro" to fast), target.dao.presets.map { it.name to it.settings })
    }

    @Test
    fun `when an existing track has no state then the backup state is applied`() = runTest {
        source.addTrack("yt_abc", "m4a", byteArrayOf(1))
        source.dao.states += PracticeStateEntity("yt_abc", slow, 1)
        val backup = exportLibrary()
        target.addTrack("yt_abc", "m4a", byteArrayOf(9))

        target.repository.restore(backup.path, Copy) {}

        assertEquals(slow, target.dao.findState("yt_abc")?.settings)
    }

    @Test
    fun `when a playlist name exists and the choice is copy then the restored one gets a number`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.dao.addPlaylist("Jazz", listOf("a"))
        val backup = exportLibrary()
        target.dao.addPlaylist("Jazz", emptyList())
        target.dao.addPlaylist("Jazz (2)", emptyList())

        target.repository.restore(backup.path, Copy) {}

        assertEquals(listOf("Jazz", "Jazz (2)", "Jazz (3)"), target.dao.playlists.map { it.name })
    }

    @Test
    fun `when a playlist name exists and the choice is overwrite then it gets the backup tracks and order`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        source.dao.addPlaylist("Jazz", listOf("b", "a"))
        val backup = exportLibrary()
        target.addTrack("a", "mp3", byteArrayOf(1))
        target.addTrack("c", "mp3", byteArrayOf(3))
        val mine = target.dao.addPlaylist("Jazz", listOf("a", "c"))

        val result = target.repository.restore(backup.path, Overwrite) {}

        assertEquals(RestoreResult.Success(tracksAdded = 1, tracksSkipped = 1, playlistsRestored = 1), result)
        val playlist = target.dao.playlists.single()
        assertEquals(mine, playlist.id)
        assertEquals(NOW, playlist.updatedAt)
        assertEquals(listOf("b", "a"), target.dao.trackIdsOf(mine))
        // Out of the playlist, still in the library.
        assertTrue(target.dao.trackExists("c"))
    }

    @Test
    fun `when the same backup is restored twice then tracks and the identical playlist are skipped`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.dao.addPlaylist("Jazz", listOf("a"))
        val backup = exportLibrary()

        target.repository.restore(backup.path, Copy) {}
        assertEquals(emptyList<String>(), target.repository.findPlaylistConflicts(backup.path))
        val second = target.repository.restore(backup.path, Copy) {}

        assertEquals(RestoreResult.Success(tracksAdded = 0, tracksSkipped = 1, playlistsRestored = 0), second)
        assertEquals(1, target.dao.tracks.size)
        assertEquals(listOf("Jazz"), target.dao.playlists.map { it.name })
    }

    @Test
    fun `when a playlist name is taken by other tracks then it is a conflict`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        source.dao.addPlaylist("Jazz", listOf("a", "b"))
        source.dao.addPlaylist(" Rock ", listOf("a"))
        source.dao.addPlaylist("Blues", listOf("b"))
        val backup = exportLibrary()
        // Same tracks, other order: a different playlist.
        target.dao.addPlaylist("Jazz", listOf("b", "a").onEach { target.addTrack(it, "mp3", byteArrayOf(0)) })
        target.dao.addPlaylist("Rock", emptyList())

        assertEquals(listOf("Jazz", "Rock"), target.repository.findPlaylistConflicts(backup.path))
    }

    @Test
    fun `when a backup has two playlists with one name then overwrite does not replace the first with the second`() = runTest {
        val manifest = BackupManifestDto(
            format = BackupManifestDto.FORMAT,
            version = BackupManifestDto.VERSION,
            createdAt = 1,
            kind = BackupKindDto.Library,
            tracks = emptyList(),
            playlists = listOf(BackupPlaylistDto("Jazz", listOf("a")), BackupPlaylistDto("Jazz", listOf("b"))),
        )
        target.addTrack("a", "mp3", byteArrayOf(1))
        target.addTrack("b", "mp3", byteArrayOf(2))

        target.repository.restore(zipOf(manifest).path, Overwrite) {}

        assertEquals(listOf("Jazz", "Jazz (2)"), target.dao.playlists.map { it.name })
        assertEquals(listOf(listOf("a"), listOf("b")), target.dao.playlists.map { target.dao.trackIdsOf(it.id) })
    }

    @Test
    fun `when the first same-name playlist is identical then overwrite keeps it and copies the second`() = runTest {
        val manifest = BackupManifestDto(
            format = BackupManifestDto.FORMAT,
            version = BackupManifestDto.VERSION,
            createdAt = 1,
            kind = BackupKindDto.Library,
            tracks = emptyList(),
            playlists = listOf(BackupPlaylistDto("Jazz", listOf("a")), BackupPlaylistDto("Jazz", listOf("b"))),
        )
        target.addTrack("a", "mp3", byteArrayOf(1))
        target.addTrack("b", "mp3", byteArrayOf(2))
        val mine = target.dao.addPlaylist("Jazz", listOf("a"))

        target.repository.restore(zipOf(manifest).path, Overwrite) {}

        assertEquals(listOf("Jazz", "Jazz (2)"), target.dao.playlists.map { it.name })
        assertEquals(listOf("a"), target.dao.trackIdsOf(mine))
    }

    @Test
    fun `when the file is not a backup then there are no conflicts to ask about`() = runTest {
        val notBackup = File(temp.root, "notes.sldbackup").apply { writeText("hello") }

        assertEquals(emptyList<String>(), target.repository.findPlaylistConflicts(notBackup.path))
    }

    @Test
    fun `when the restore fails after copying audio then the copied files are removed`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        source.dao.addPlaylist("Jazz", listOf("a", "b"))
        val backup = exportLibrary()
        target.addTrack("existing", "mp3", byteArrayOf(9))
        target.dao.playlistInsertError = IOException("disk full")

        val result = target.repository.restore(backup.path, Copy) {}

        assertTrue(result is RestoreResult.Failure)
        // The fake has no rollback: Room undoes the rows, the repository undoes the files.
        assertEquals(listOf("existing.mp3"), target.libraryDir.list()?.toList())
    }

    @Test
    fun `when the restore is cancelled then the copied files are removed`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.addTrack("b", "mp3", byteArrayOf(2))
        val backup = exportLibrary()

        val job = launch {
            target.repository.restore(backup.path, Copy) { progress -> if (progress.done == 1) cancel() }
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(0, target.libraryDir.list()?.size)
        assertEquals(0, target.stagingDir.list()?.size)
        assertEquals(emptyList<TrackEntity>(), target.dao.tracks)
    }

    @Test
    fun `when a restore died with the process then its staging files are removed`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        val backup = exportLibrary()
        target.stagingDir.mkdirs()
        File(target.stagingDir, "old.mp3").writeBytes(byteArrayOf(5))

        target.repository.restore(backup.path, Copy) {}

        assertEquals(0, target.stagingDir.list()?.size)
    }

    @Test
    fun `when the export fails then the previous backup is kept`() = runTest {
        source.addTrack("a", "mp3", byteArrayOf(1))
        source.repository.export(BackupScope.Track("a"), "old.sldbackup") {}
        File(source.dao.tracks.single().filePath).setReadable(false)

        val result = source.repository.export(BackupScope.Track("a"), "new.sldbackup") {}

        assertTrue(result is BackupExportResult.Failure)
        assertEquals(listOf("old.sldbackup"), source.backupDir.list()?.toList())
    }

    @Test
    fun `when the file is not a backup then nothing changes`() = runTest {
        val notBackup = temp.newFile("song.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals(RestoreResult.InvalidFile, target.repository.restore(notBackup.path, Copy) {})
        assertEquals(emptyList<TrackEntity>(), target.dao.tracks)
        assertEquals(0, target.libraryDir.list()?.size)
    }

    @Test
    fun `when an audio entry is missing then nothing is kept`() = runTest {
        val manifest = BackupManifestDto(
            format = BackupManifestDto.FORMAT,
            version = BackupManifestDto.VERSION,
            createdAt = 0,
            kind = BackupKindDto.Library,
            tracks = listOf(
                BackupTrackDto(id = "a", title = "A", addedAt = 0, audio = "audio/a.mp3"),
                BackupTrackDto(id = "b", title = "B", addedAt = 0, audio = "audio/b.mp3"),
            ),
        )
        val zip = zipOf(manifest, "audio/a.mp3" to byteArrayOf(1))

        assertEquals(RestoreResult.InvalidFile, target.repository.restore(zip.path, Copy) {})
        assertEquals(0, target.libraryDir.list()?.size)
        assertEquals(emptyList<TrackEntity>(), target.dao.tracks)
    }

    @Test
    fun `when the backup is from a newer version then it is unsupported`() = runTest {
        val zip = File(temp.root, "new.sldbackup")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("manifest.json"))
            it.write("""{"format":"sldbackup","version":3}""".toByteArray())
            it.closeEntry()
        }

        assertEquals(RestoreResult.UnsupportedVersion(3), target.repository.restore(zip.path, Copy) {})
    }

    private suspend fun exportLibrary(): File {
        val result = source.repository.export(BackupScope.Library, "library.sldbackup") {}
        return File((result as BackupExportResult.Success).file.path)
    }

    private fun readManifest(file: File) = file.inputStream().use { BackupArchive().read(it, { emptyMap() }, {}) }

    private fun zipOf(manifest: BackupManifestDto, vararg audio: Pair<String, ByteArray>): File {
        val file = File(temp.root, "custom.sldbackup")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(BackupArchive.json.encodeToString(BackupManifestDto.serializer(), manifest).toByteArray())
            zip.closeEntry()
            audio.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    /** One device: its database, its library folder and a repository over them. */
    private inner class Library(name: String) {
        val dao = FakeBackupDao()
        val libraryDir: File = temp.newFolder(name, "library")
        val backupDir = File(temp.root, "$name/backups")
        val stagingDir = File(temp.root, "$name/staging")
        val repository = DefaultBackupRepository(
            dao = dao,
            transactions = FakeTransactionRunner(),
            store = RoomLibraryStore(FakeTrackDao(), LegacyJsonLibraryIndex(libraryDir) { _, _ -> }, libraryDir) { _, _ -> },
            documents = FakeFileDocumentDataSource(),
            archive = BackupArchive(),
            backupDir = backupDir,
            stagingDir = stagingDir,
            dispatcher = UnconfinedTestDispatcher(),
            now = { NOW },
            logError = { _, _ -> },
        )

        fun addTrack(id: String, extension: String, bytes: ByteArray, title: String = id): TrackEntity {
            val file = File(libraryDir, "$id.$extension").apply { writeBytes(bytes) }
            return TrackEntity(id, title, file.path, null, addedAt = dao.tracks.size.toLong()).also { dao.tracks += it }
        }
    }

    private companion object {
        const val NOW = 42L
    }
}
