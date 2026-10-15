package com.damianogiusti.slowdowner.domain.update

/** A dotted version, as in the release tags: v1.0.12 is 1.0.12. Missing parts count as zero. */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    override fun equals(other: Any?) = other is AppVersion && compareTo(other) == 0

    override fun hashCode() = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString() = parts.joinToString(".")

    companion object {
        /** Null for anything that is not a version, like "dev". */
        fun parse(text: String): AppVersion? {
            val parts = text.trim().removePrefix("v").split('.').map { it.toIntOrNull()?.takeIf { part -> part >= 0 } }
            if (parts.isEmpty() || parts.any { it == null }) return null
            return AppVersion(parts.filterNotNull())
        }
    }
}
