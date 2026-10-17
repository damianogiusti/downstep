package com.damianogiusti.slowdowner.domain.export

/** Renders the open track with the current speed, pitch and effects into MP3 files. */
interface ExportRepository {
    /**
     * Renders every item, in order, as one batch. [onProgress] gets 0..1 over the whole batch.
     * Cancelling the caller, or any failure, stops the render and deletes every file of the batch.
     */
    suspend fun exportOpenTrack(items: List<ExportItem>, onProgress: (Float) -> Unit): ExportResult

    /** Copies [file] into a document the user picked. Returns false if the copy failed. */
    suspend fun saveTo(file: ExportedFile, contentUri: String): Boolean

    /**
     * Copies [files] next to each other into the folder the user picked, never over a file already
     * there (the copy gets a " (2)" instead). Returns false if any copy failed.
     */
    suspend fun saveAllTo(files: List<ExportedFile>, folder: String): Boolean
}
