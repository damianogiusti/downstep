package com.damianogiusti.slowdowner.data.logging

import com.damianogiusti.slowdowner.logging.Logger
import java.io.IOException

/**
 * A full disk, a missing permission, a file that went away: the user's computer, not a bug of ours.
 * Those stay warnings; anything else is an error, and an issue to fix.
 */
internal fun Logger.failure(message: String, error: Throwable, properties: Map<String, String> = emptyMap()) =
    if (error is IOException) w(message, error, properties) else e(message, error, properties)
