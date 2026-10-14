package com.damianogiusti.slowdowner.data.platform

internal actual fun platformLogError(tag: String, message: String, error: Throwable) {
    System.err.println("E/$tag: $message")
    error.printStackTrace()
}

internal actual fun platformLogWarning(tag: String, message: String, error: Throwable) {
    System.err.println("W/$tag: $message: $error")
}
