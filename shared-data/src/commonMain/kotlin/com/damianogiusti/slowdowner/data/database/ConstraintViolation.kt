package com.damianogiusti.slowdowner.data.database

/** True when a write broke a UNIQUE or foreign-key constraint. Each SQLite driver throws its own type. */
internal expect fun isConstraintViolation(error: Throwable): Boolean
