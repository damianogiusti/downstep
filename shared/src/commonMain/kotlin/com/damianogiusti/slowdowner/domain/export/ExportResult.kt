package com.damianogiusti.slowdowner.domain.export

/** A rendered track, ready to share or save. [path] is app-private. */
data class ExportedFile(val path: String, val name: String)

sealed interface ExportResult {
    data class Success(val file: ExportedFile) : ExportResult
    /** No library track is open: the engine has nothing to render. */
    data object NothingOpen : ExportResult
    data class Failure(val cause: Throwable) : ExportResult
}
