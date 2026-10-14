package com.damianogiusti.slowdowner.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Not a backup, damaged, or with unsafe entries. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A backup written by a newer version of the format. */
class UnsupportedBackupVersionException(val version: Int) : Exception("Unsupported backup version $version")

/** What goes into a zip: the manifest, then each audio entry it lists with the file to copy. */
class BackupContents(val manifest: BackupManifestDto, val audioFiles: Map<String, File>)

/**
 * Reads and writes `.sldbackup` zips as streams: audio never sits in memory, and a restore
 * reads straight from the picked document without a temporary copy. Blocking.
 */
class BackupArchive {

    fun write(target: File, contents: BackupContents, onAudioWritten: (Int) -> Unit) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry(BackupEntryNames.MANIFEST))
            zip.write(json.encodeToString(BackupManifestDto.serializer(), contents.manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // Audio is already compressed, so deflating it costs CPU for almost no gain.
            // DEFLATED at level 0 still writes in one pass; STORED would need a CRC pass over
            // every file before writing it, and ZipInputStream reads both.
            zip.setLevel(Deflater.NO_COMPRESSION)
            contents.audioFiles.entries.forEachIndexed { index, (entry, file) ->
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                onAudioWritten(index + 1)
            }
        }
    }

    /**
     * Streams a backup. The manifest must be the first entry: it is validated, then [plan]
     * says where each wanted audio entry goes. Other entries are skipped without reading.
     * Throws [InvalidBackupException], [UnsupportedBackupVersionException] or an IOException.
     */
    fun read(
        input: InputStream,
        plan: (BackupManifestDto) -> Map<String, File>,
        onAudioExtracted: (Int) -> Unit,
    ): BackupManifestDto = ZipInputStream(input.buffered()).use { zip ->
        val manifest = zip.readManifest()
        val targets = plan(manifest)
        val pending = targets.keys.toMutableSet()
        var extracted = 0
        while (true) {
            val entry = zip.nextSafeEntry() ?: break
            if (!pending.remove(entry.name)) continue
            val target = targets.getValue(entry.name)
            target.outputStream().use { zip.copyTo(it) }
            onAudioExtracted(++extracted)
        }
        if (pending.isNotEmpty()) throw InvalidBackupException("Missing audio entries: $pending")
        manifest
    }

    /** Only the manifest, validated like [read] does. Stops before the audio. */
    fun readManifest(input: InputStream): BackupManifestDto = ZipInputStream(input.buffered()).use { it.readManifest() }

    private fun ZipInputStream.readManifest(): BackupManifestDto {
        val first = nextSafeEntry() ?: throw InvalidBackupException("Empty archive")
        if (first.name != BackupEntryNames.MANIFEST) throw InvalidBackupException("The manifest is not the first entry")
        return parseManifest(readAtMost(MAX_MANIFEST_BYTES)).also(::validate)
    }

    private fun ZipInputStream.nextSafeEntry(): ZipEntry? {
        val entry = nextEntry ?: return null
        if (!BackupEntryNames.isSafeEntryName(entry.name)) throw InvalidBackupException("Unsafe entry name: ${entry.name}")
        return entry
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > limit) throw InvalidBackupException("Manifest larger than $limit bytes")
        }
        return output.toByteArray()
    }

    private fun parseManifest(bytes: ByteArray): BackupManifestDto {
        // Format and version first: a newer manifest may not decode into today's DTOs.
        val root = try {
            json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Unreadable manifest", e)
        }
        val format = (root["format"] as? JsonPrimitive)?.contentOrNull
        if (format != BackupManifestDto.FORMAT) throw InvalidBackupException("Unknown format: $format")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: throw InvalidBackupException("No version")
        if (version > BackupManifestDto.VERSION) throw UnsupportedBackupVersionException(version)
        if (version < 1) throw InvalidBackupException("Invalid version: $version")
        return try {
            json.decodeFromJsonElement<BackupManifestDto>(root)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw InvalidBackupException("Invalid manifest", e)
        }
    }

    private fun validate(manifest: BackupManifestDto) {
        val ids = HashSet<String>()
        manifest.tracks.forEach { track ->
            if (!BackupEntryNames.isSafeId(track.id)) throw InvalidBackupException("Unsafe track id: ${track.id}")
            if (!ids.add(track.id)) throw InvalidBackupException("Duplicate track id: ${track.id}")
            BackupEntryNames.audioExtension(track.id, track.audio)
                ?: throw InvalidBackupException("Unexpected audio entry: ${track.audio}")
        }
    }

    internal companion object {
        /** Far above any real library: the manifest holds only text. Guards against zip bombs. */
        const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
        private const val BUFFER_SIZE = 8 * 1024

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
