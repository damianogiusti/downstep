package com.damianogiusti.slowdowner.data.stems

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Stem rows in Room; stem files in `<stemsDir>/<trackId>/`, outside the library folder and
 * outside backups.
 */
class RoomStemsStore(
    private val dao: StemsDao,
    private val stemsDir: File,
) : StemsStore {

    override fun observe(trackId: String): Flow<TrackStemsDto?> =
        dao.observe(trackId).map { relation ->
            relation?.let {
                TrackStemsDto(
                    trackId = it.state.trackId,
                    model = it.state.model,
                    mode = it.state.mode,
                    stems = it.stems.map { stem -> StemFileDto(stem.stem, stem.fileName, stem.gain) },
                )
            }
        }

    override fun observeSeparatedTrackIds(): Flow<List<String>> = dao.observeSeparatedTrackIds()

    override suspend fun setMode(trackId: String, mode: String) = dao.updateMode(trackId, mode)

    override suspend fun setGain(trackId: String, stem: String, gain: Float) = dao.updateGain(trackId, stem, gain)

    override suspend fun add(stems: TrackStemsDto) = dao.replace(
        state = TrackStemStateEntity(stems.trackId, stems.model, stems.mode),
        stems = stems.stems.map { TrackStemEntity(stems.trackId, it.stem, it.fileName, it.gain) },
    )

    override suspend fun delete(trackId: String) {
        val dir = resolve(trackId)
        // Rows first: a crash in between leaves orphan files, never rows pointing at nothing.
        dao.delete(trackId)
        deleteFolder(dir)
    }

    override fun dirFor(trackId: String): File = resolve(trackId).apply { mkdirs() }

    private fun resolve(trackId: String): File {
        require(isSafeName(trackId)) { "Not a valid track id for a folder name" }
        return File(stemsDir, trackId)
    }

    private fun isSafeName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' } &&
            !name.contains("..")

    /**
     * Only regular files directly inside the folder, then the folder. No recursion and no link is
     * followed: a link, or a folder that is one, is left alone.
     */
    private fun deleteFolder(dir: File) {
        val path = dir.toPath()
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) return
        dir.listFiles()?.forEach { child ->
            if (Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS)) child.delete()
        }
        // Fails, on purpose, while something else is still inside.
        dir.delete()
    }
}
