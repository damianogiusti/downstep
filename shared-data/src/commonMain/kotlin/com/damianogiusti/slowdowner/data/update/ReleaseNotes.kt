package com.damianogiusti.slowdowner.data.update

/**
 * The changes in a release's notes, as plain lines for the update dialog. The release workflow writes
 * them first, as a list, then a line "---" and the install instructions. Notes without that line
 * (releases before 1.2.0) have no changes to show: their text is only about installing.
 */
internal object ReleaseNotes {
    private const val SEPARATOR = "---"
    // A dialog, not a page: a long list would push the buttons away.
    private const val MAX_LINES = 8
    private const val MAX_LINE_LENGTH = 200

    internal val BULLET = Regex("""^[-*]\s+""")
    private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    // Bold and code only: a single * or _ can be part of the text.
    private val EMPHASIS = Regex("""\*\*|`""")

    fun changes(body: String?): List<String> {
        val lines = body?.lines()?.map { it.trim() } ?: return emptyList()
        val end = lines.indexOf(SEPARATOR).takeIf { it >= 0 } ?: return emptyList()
        return lines.subList(0, end)
            .filter { BULLET.containsMatchIn(it) }
            .map { plain(it.replaceFirst(BULLET, "")) }
            .filter { it.isNotEmpty() }
            .take(MAX_LINES)
    }

    // Plain text only: the dialog shows no links, and a stray marker should not show either.
    internal fun plain(line: String): String =
        line.replace(LINK, "$1").replace(EMPHASIS, "").trim().take(MAX_LINE_LENGTH)
}
