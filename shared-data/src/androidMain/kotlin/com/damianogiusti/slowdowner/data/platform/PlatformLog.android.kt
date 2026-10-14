package com.damianogiusti.slowdowner.data.platform

import android.util.Log

internal actual fun platformLogError(tag: String, message: String, error: Throwable) {
    Log.e(tag, message, error)
}

internal actual fun platformLogWarning(tag: String, message: String, error: Throwable) {
    Log.w(tag, message, error)
}
