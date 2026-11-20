package com.damianogiusti.slowdowner.domain.export

/** Renders the open track into MP3 files. */
interface ExportRepository {
    /**
     * Renders [range] of the open track with the current speed, pitch and effects, for sharing or
     * saving. [onProgress] gets 0..1 over [range]. Cancelling the caller stops the render and
     * deletes the partial file.
     */
    suspend fun exportOpenTrack(fileName: String, range: RenderRange, onProgress: (Float) -> Unit): ExportResult

    /**
     * Renders [range] of the open track with its original sound (speed 100%, no pitch shift, no
     * effects), to become a library track. Same progress and cancellation as [exportOpenTrack].
     */
    suspend fun renderOriginal(range: RenderRange, onProgress: (Float) -> Unit): ExportResult

    /** Copies [file] into a document the user picked. Returns false if the copy failed. */
    suspend fun saveTo(file: ExportedFile, contentUri: String): Boolean
}
