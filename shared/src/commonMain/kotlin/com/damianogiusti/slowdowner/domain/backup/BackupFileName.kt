package com.damianogiusti.slowdowner.domain.backup

import kotlinx.datetime.LocalDateTime

internal object BackupFileName {
    const val EXTENSION = "sldbackup"
    /** The app is Italian only; the library backup has no title of its own. */
    const val LIBRARY_LABEL = "Libreria"

    /** Spaces too: a name without them needs no quoting in a shell or a link. */
    private val SEPARATOR_RUNS = Regex("""[\s\\/:*?"<>|\x00-\x1F\x7F]+""")
    private const val MAX_LABEL_LENGTH = 80

    /**
     * "Downstep-Autumn-Leaves-20261013143005.sldbackup". Down to the second: the same item exported
     * twice must not get the name of the backup still in the cache, which Windows will not replace.
     */
    fun of(label: String, time: LocalDateTime): String {
        val safeLabel = label.replace(SEPARATOR_RUNS, "-").trim('-').take(MAX_LABEL_LENGTH).trim('-').ifEmpty { "backup" }
        return "Downstep-$safeLabel-${stamp(time)}.$EXTENSION"
    }

    /** yyyyMMddHHmmss */
    private fun stamp(time: LocalDateTime): String = with(time) {
        listOf(month.ordinal + 1, day, hour, minute, second)
            .joinToString("", prefix = year.toString().padStart(4, '0')) { it.toString().padStart(2, '0') }
    }
}
