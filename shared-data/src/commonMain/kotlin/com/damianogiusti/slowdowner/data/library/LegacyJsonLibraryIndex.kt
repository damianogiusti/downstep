package com.damianogiusti.slowdowner.data.library

import com.damianogiusti.slowdowner.data.platform.platformLogError
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * The `index.json` written by the first version of the library. Read once to move the
 * library into Room, then renamed: the file stays on disk as a backup.
 */
class LegacyJsonLibraryIndex(
    libraryDir: File,
    private val logError: (String, Throwable) -> Unit = { message, error -> platformLogError(TAG, message, error) },
) {

    private val indexFile = File(libraryDir, "index.json")
    private val retiredFile = File(libraryDir, "index.json.imported")

    fun exists(): Boolean = indexFile.exists()

    /** Entries whose audio file is gone are skipped, as the JSON store did. */
    fun read(): List<LibraryTrackDto> {
        val entries = try {
            json.decodeFromString<List<LegacyLibraryEntryDto>>(indexFile.readText())
        } catch (e: SerializationException) {
            logError("Unreadable legacy library index, nothing imported", e)
            return emptyList()
        } catch (e: IOException) {
            logError("Cannot read legacy library index", e)
            return emptyList()
        }
        return entries
            .map { it.toDto() }
            .filter { File(it.filePath).exists() }
    }

    fun retire() {
        if (!indexFile.renameTo(retiredFile)) throw IOException("Cannot rename $indexFile")
    }

    @Serializable
    private data class LegacyLibraryEntryDto(
        val id: String,
        val title: String,
        val filePath: String,
        val sourceUrl: String = "",
        val addedAt: Long,
    ) {
        // The JSON store wrote a missing URL as "".
        fun toDto() = LibraryTrackDto(id, title, filePath, sourceUrl.ifEmpty { null }, addedAt)
    }

    private companion object {
        const val TAG = "SlowDowner"
        val json = Json { ignoreUnknownKeys = true }
    }
}
