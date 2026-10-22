package com.damianogiusti.slowdowner.desktop

import java.time.ZoneId
import java.util.Locale

/**
 * What "App started" says about the computer, never about the person. The country comes from the
 * system's region and time zone, not from the network: no lookup, no IP. A region can be left on
 * United States by someone in Italy: country and time zone are read together.
 *
 * [hasLaunchHistory] lives in the Java preferences, outside the data folder: a new installation id
 * with a history means the data folder was deleted, not a new person.
 */
internal fun launchProperties(
    isNewInstallation: Boolean?,
    hasLaunchHistory: Boolean,
    region: Locale = Locale.getDefault(Locale.Category.FORMAT),
    display: Locale = Locale.getDefault(Locale.Category.DISPLAY),
    timeZone: ZoneId = ZoneId.systemDefault(),
): Map<String, String> = buildMap {
    isNewInstallation?.let { put("installation", if (it) "new" else "existing") }
    put("history", if (hasLaunchHistory) "returning" else "fresh")
    put("country", region.country.ifEmpty { "unknown" })
    put("language", display.language.ifEmpty { "unknown" })
    put("timeZone", timeZone.id)
}
