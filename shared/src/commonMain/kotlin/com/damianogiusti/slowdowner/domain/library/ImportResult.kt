package com.damianogiusti.slowdowner.domain.library

sealed interface ImportResult {
    data class Success(val track: LibraryTrack) : ImportResult
    /** The content exists but cannot be fetched (private, removed, no usable audio). */
    data object Unavailable : ImportResult
    data object NetworkError : ImportResult
    data class Failure(val cause: Throwable) : ImportResult
}
