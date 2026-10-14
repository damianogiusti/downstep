package com.damianogiusti.slowdowner.data.database

import androidx.sqlite.SQLiteException

// The bundled driver reports SQLITE_CONSTRAINT only in the message, e.g. "UNIQUE constraint failed".
internal actual fun isConstraintViolation(error: Throwable): Boolean =
    error is SQLiteException && error.message.orEmpty().contains("constraint failed", ignoreCase = true)
