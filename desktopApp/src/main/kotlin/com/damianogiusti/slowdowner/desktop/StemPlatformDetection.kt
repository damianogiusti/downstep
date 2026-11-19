package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.domain.stems.StemPlatform

/**
 * Intel Macs run the separation slowly and may run out of memory: it stays available, with a notice.
 * A JVM running under Rosetta also reports x86_64, which is the same warning for the same reason.
 */
internal fun detectStemPlatform(osName: String? = System.getProperty("os.name"), osArch: String? = System.getProperty("os.arch")): StemPlatform {
    val isMac = osName.orEmpty().startsWith("Mac")
    val isIntel = osArch.orEmpty().lowercase() in setOf("x86_64", "amd64")
    return StemPlatform(isDeprecated = isMac && isIntel)
}
