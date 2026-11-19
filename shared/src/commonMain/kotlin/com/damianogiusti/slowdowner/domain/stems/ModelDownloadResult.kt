package com.damianogiusti.slowdowner.domain.stems

sealed interface ModelDownloadResult {
    data object Success : ModelDownloadResult

    /** No connection, a dropped one, a server that failed, or a full disk: trying again may work. */
    data object NetworkError : ModelDownloadResult

    /** The file came whole but is not the one expected (size or checksum). It was deleted. */
    data object Corrupted : ModelDownloadResult

    data class Failure(val cause: Throwable) : ModelDownloadResult
}
