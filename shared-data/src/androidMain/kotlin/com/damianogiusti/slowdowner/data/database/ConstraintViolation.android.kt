package com.damianogiusti.slowdowner.data.database

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.SQLiteException

// The framework helper throws SQLiteConstraintException; Room's driver layer may wrap it in an
// androidx SQLiteException that names the constraint only in the message.
internal actual fun isConstraintViolation(error: Throwable): Boolean =
    error is SQLiteConstraintException ||
        (error is SQLiteException && error.message.orEmpty().contains("constraint failed", ignoreCase = true))
