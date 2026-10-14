package com.damianogiusti.slowdowner.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val archive = BackupArchive()

    private val manifest = BackupManifestDto(
        format = BackupManifestDto.FORMAT,
        version = BackupManifestDto.VERSION,
        createdAt = 42,
        kind = BackupKindDto.Library,
        tracks = listOf(
            BackupTrackDto(
                id = "yt_abc",
                title = "Autumn Leaves",
                sourceUrl = "https://youtu.be/abc",
                addedAt = 1,
                audio = "audio/yt_abc.m4a",
                practiceState = BackupSettingsDto(75f, -2f, 0.2f, 0.4f, isLoopOn = true),
                presets = listOf(BackupPresetDto("Solo", BackupSettingsDto(60f, 0f, 0f, 1f, isLoopOn = false))),
            ),
            BackupTrackDto(id = "local_1", title = "Blackbird", addedAt = 2, audio = "audio/local_1.mp3"),
        ),
        playlists = listOf(BackupPlaylistDto("Jazz", listOf("local_1", "yt_abc"))),
    )

    @Test
    fun `when a backup is written and read then manifest and audio come back`() {
        val first = audioFile("a.m4a", byteArrayOf(1, 2, 3))
        val second = audioFile("b.mp3", ByteArray(100_000) { it.toByte() })
        val zip = temp.newFile("backup.sldbackup")
        val written = mutableListOf<Int>()

        archive.write(zip, BackupContents(manifest, mapOf("audio/yt_abc.m4a" to first, "audio/local_1.mp3" to second))) {
            written += it
        }
        val targets = mapOf("audio/yt_abc.m4a" to File(temp.root, "out1"), "audio/local_1.mp3" to File(temp.root, "out2"))
        val extracted = mutableListOf<Int>()
        val read = zip.inputStream().use { input -> archive.read(input, { targets }, { extracted += it }) }

        assertEquals(manifest, read)
        assertArrayEquals(first.readBytes(), File(temp.root, "out1").readBytes())
        assertArrayEquals(second.readBytes(), File(temp.root, "out2").readBytes())
        assertEquals(listOf(1, 2), written)
        assertEquals(listOf(1, 2), extracted)
    }

    @Test
    fun `when a backup is written then the manifest is the first entry`() {
        val zip = temp.newFile("backup.sldbackup")

        archive.write(zip, BackupContents(manifest, mapOf("audio/yt_abc.m4a" to audioFile("a.m4a", byteArrayOf(1))))) {}

        ZipFile(zip).use { assertEquals(listOf("manifest.json", "audio/yt_abc.m4a"), it.entries().toList().map(ZipEntry::getName)) }
    }

    @Test
    fun `when only some entries are planned then the others are not extracted`() {
        val zip = temp.newFile("backup.sldbackup")
        archive.write(
            zip,
            BackupContents(
                manifest,
                mapOf("audio/yt_abc.m4a" to audioFile("a.m4a", byteArrayOf(1)), "audio/local_1.mp3" to audioFile("b.mp3", byteArrayOf(2))),
            ),
        ) {}
        val target = File(temp.root, "only")

        zip.inputStream().use { input -> archive.read(input, { mapOf("audio/local_1.mp3" to target) }, {}) }

        assertArrayEquals(byteArrayOf(2), target.readBytes())
        assertEquals(listOf("a.m4a", "b.mp3", "backup.sldbackup", "only"), temp.root.list()?.sorted())
    }

    @Test
    fun `when an entry climbs out of its folder then the backup is rejected`() {
        val zip = rawZip("manifest.json" to manifestJson(), "audio/../../evil.sh" to "x")

        assertInvalid(zip)
    }

    @Test
    fun `when an entry has an absolute path then the backup is rejected`() {
        assertInvalid(rawZip("manifest.json" to manifestJson(), "/data/evil" to "x"))
    }

    @Test
    fun `when the manifest is not the first entry then the backup is rejected`() {
        assertInvalid(rawZip("audio/yt_abc.m4a" to "x", "manifest.json" to manifestJson()))
    }

    @Test
    fun `when the format is not sldbackup then the backup is rejected`() {
        assertInvalid(rawZip("manifest.json" to manifestJson().replace("\"sldbackup\"", "\"other\"")))
    }

    @Test
    fun `when the file is not a zip then the backup is rejected`() {
        val file = temp.newFile("notes.txt").apply { writeText("hello") }

        assertInvalid(file)
    }

    @Test
    fun `when the version is newer then it is reported as unsupported`() {
        val zip = rawZip("manifest.json" to """{"format":"sldbackup","version":2,"kind":"something-new"}""")

        try {
            zip.inputStream().use { input -> archive.read(input, { emptyMap() }, {}) }
            fail("Expected UnsupportedBackupVersionException")
        } catch (e: UnsupportedBackupVersionException) {
            assertEquals(2, e.version)
        }
    }

    @Test
    fun `when the manifest has unknown keys then they are ignored`() {
        val json = manifestJson().replaceFirst("{", """{"futureField":{"a":1},""")
        val zip = rawZip("manifest.json" to json)

        val read = zip.inputStream().use { input -> archive.read(input, { emptyMap() }, {}) }

        assertEquals(manifest, read)
    }

    @Test
    fun `when a track id could escape the library folder then the backup is rejected`() {
        val json = manifestJson().replace("\"local_1\"", "\"../local_1\"")

        assertInvalid(rawZip("manifest.json" to json))
    }

    @Test
    fun `when the audio entry does not match the track id then the backup is rejected`() {
        val json = manifestJson().replace("audio/local_1.mp3", "audio/other.mp3")

        assertInvalid(rawZip("manifest.json" to json))
    }

    @Test
    fun `when a planned audio entry is missing then the backup is rejected`() {
        val zip = rawZip("manifest.json" to manifestJson())

        try {
            zip.inputStream().use { input -> archive.read(input, { mapOf("audio/local_1.mp3" to File(temp.root, "x")) }, {}) }
            fail("Expected InvalidBackupException")
        } catch (e: InvalidBackupException) {
            assertFalse(File(temp.root, "x").exists())
        }
    }

    @Test
    fun `when the manifest is too big then the backup is rejected`() {
        val padding = " ".repeat(BackupArchive.MAX_MANIFEST_BYTES)

        assertInvalid(rawZip("manifest.json" to manifestJson() + padding))
    }

    private fun assertInvalid(zip: File) {
        try {
            zip.inputStream().use { input -> archive.read(input, { emptyMap() }, {}) }
            fail("Expected InvalidBackupException")
        } catch (e: InvalidBackupException) {
            // Expected.
        }
    }

    private fun manifestJson() = BackupArchive.json.encodeToString(BackupManifestDto.serializer(), manifest)

    private fun audioFile(name: String, bytes: ByteArray) = temp.newFile(name).apply { writeBytes(bytes) }

    private fun rawZip(vararg entries: Pair<String, String>): File {
        val file = File(temp.root, "raw-${entries.size}-${System.nanoTime()}.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
