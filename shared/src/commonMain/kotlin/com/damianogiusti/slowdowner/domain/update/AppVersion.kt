package com.damianogiusti.slowdowner.domain.update

/**
 * A dotted version, as in the release tags: v1.0.12 is 1.0.12. Missing parts count as zero.
 *
 * A build that is not a release has a dev marker after the numbers, and maybe the commit:
 * 1.1.1-dev+3f2a9c1 is built from the sources after v1.1.1 (see desktopApp/build.gradle.kts). It
 * compares as its numbers: the marker only says that this is no release, see [isDevelopment].
 */
class AppVersion private constructor(
    private val parts: List<Int>,
    private val suffix: String,
) : Comparable<AppVersion> {

    /** A build from the sources, not a release: it never updates itself on its own. */
    val isDevelopment: Boolean get() = suffix.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0

    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString() = parts.joinToString(".") + suffix

    companion object {
        /** Null for anything that is not a version, like "dev". */
        fun parse(text: String): AppVersion? {
            val trimmed = text.trim().removePrefix("v")
            val suffix = DEV_SUFFIX.find(trimmed)?.value.orEmpty()
            val parts = trimmed.removeSuffix(suffix).split('.').map { it.toIntOrNull()?.takeIf { part -> part >= 0 } }
            if (parts.isEmpty() || parts.any { it == null }) return null
            return AppVersion(parts.filterNotNull(), suffix)
        }

        /** -dev, then the commit as build metadata: +3f2a9c1. */
        private val DEV_SUFFIX = Regex("""-dev(\+[0-9A-Za-z.-]+)?$""")
    }
}
