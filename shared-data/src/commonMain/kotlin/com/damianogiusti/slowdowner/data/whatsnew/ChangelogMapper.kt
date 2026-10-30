package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.data.update.ReleaseNotes
import com.damianogiusti.slowdowner.domain.update.AppVersion
import com.damianogiusti.slowdowner.domain.whatsnew.ChangelogSection

/**
 * desktopApp/CHANGELOG.txt: a header, then a heading "## 1.2.0" per version, each followed by "- " lines.
 * Text before the first heading, and headings that are not a version, are not changes.
 */
object ChangelogMapper {
    private val HEADING = Regex("""^##\s+(\S+)\s*$""")

    fun toSections(text: String): List<ChangelogSection> {
        val sections = mutableListOf<ChangelogSection>()
        var version: AppVersion? = null
        var lines = mutableListOf<String>()
        fun close() {
            version?.let { sections += ChangelogSection(it, lines) }
            lines = mutableListOf()
        }
        text.lines().map { it.trim() }.forEach { line ->
            val heading = HEADING.find(line)
            when {
                heading != null -> {
                    close()
                    version = AppVersion.parse(heading.groupValues[1])
                }
                version != null && ReleaseNotes.BULLET.containsMatchIn(line) -> {
                    val plain = ReleaseNotes.plain(line.replaceFirst(ReleaseNotes.BULLET, ""))
                    if (plain.isNotEmpty()) lines += plain
                }
            }
        }
        close()
        return sections
    }
}
