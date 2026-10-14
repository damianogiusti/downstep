package com.damianogiusti.slowdowner.domain.export

/** Renders the open track with the current speed, pitch and effects into an MP3 file. */
interface ExportRepository {
    /** [onProgress] gets 0..1. Cancelling the caller stops the render and deletes the partial file. */
    suspend fun exportOpenTrack(fileName: String, onProgress: (Float) -> Unit): ExportResult

    /** Copies [file] into a document the user picked. Returns false if the copy failed. */
    suspend fun saveTo(file: ExportedFile, contentUri: String): Boolean
}
