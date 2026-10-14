package com.damianogiusti.slowdowner.data.platform

/** Logcat on Android, stderr on desktop. For failures that have no UI feedback. */
internal expect fun platformLogError(tag: String, message: String, error: Throwable)

internal expect fun platformLogWarning(tag: String, message: String, error: Throwable)
