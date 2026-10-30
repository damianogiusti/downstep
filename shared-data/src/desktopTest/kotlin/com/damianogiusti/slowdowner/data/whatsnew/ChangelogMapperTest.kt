package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.domain.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogMapperTest {

    private val shape = """
        # Changelog

        What changes for people who use Downstep. - not a change.

        ## 1.3.0

        - Loops remember **where** you left them. See [the page](https://example.com).
        - Bug fixes and small improvements.

        ## 1.2.0

        - Normalize volume.
    """.trimIndent()

    private fun sections(text: String) = ChangelogMapper.toSections(text).map { it.version.toString() to it.lines }

    @Test
    fun `each heading with its bullets is a section, in file order`() {
        assertEquals(
            listOf(
                "1.3.0" to listOf("Loops remember where you left them. See the page.", "Bug fixes and small improvements."),
                "1.2.0" to listOf("Normalize volume."),
            ),
            sections(shape),
        )
    }

    @Test
    fun `text before the first heading is not a change`() {
        assertEquals(2, ChangelogMapper.toSections(shape).size)
    }

    @Test
    fun `a heading that is not a version, and its bullets, are skipped`() {
        assertEquals(
            listOf("1.2.0" to listOf("One")),
            sections("## Unreleased\n- Draft\n\n## 1.2.0\n- One\n"),
        )
    }

    @Test
    fun `Windows line endings give the same sections`() {
        assertEquals(sections(shape), sections(shape.replace("\n", "\r\n")))
    }

    @Test
    fun `an empty text has no sections`() {
        assertTrue(ChangelogMapper.toSections("").isEmpty())
    }

    @Test
    fun `the real changelog of the app parses, and every section has lines`() {
        // Gradle runs the tests in the module folder.
        val file = File("../desktopApp/CHANGELOG.txt")
        assertTrue("CHANGELOG.txt not found at ${file.absolutePath}", file.isFile)

        val sections = ChangelogMapper.toSections(file.readText())

        assertTrue(sections.isNotEmpty())
        assertTrue(sections.all { it.lines.isNotEmpty() })
        assertEquals(AppVersion.parse("1.2.0"), sections.last().version)
    }
}
