package com.damianogiusti.slowdowner.domain.export

/** A rendered track, ready to share or save. [path] is app-private. */
data class ExportedFile(val path: String, val name: String)

sealed interface ExportResult {
    /** Every file of the batch, in the order asked for: the full track first, then the loops. */
    data class Success(val files: List<ExportedFile>) : ExportResult
    /** No library track is open: the engine has nothing to render. */
    data object NothingOpen : ExportResult
    /** Only the loops were asked for, and the track has none. */
    data object NoLoops : ExportResult
    data class Failure(val cause: Throwable) : ExportResult
}
