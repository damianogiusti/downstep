package com.damianogiusti.slowdowner.data.whatsnew

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ResourceChangelogDataSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `when the file is in the resources folder then its text is read`() {
        folder.newFile("CHANGELOG.txt").writeText("## 1.3.0\n- One\n")

        assertEquals("## 1.3.0\n- One\n", ResourceChangelogDataSource(folder.root, ConsoleLogger()).read())
    }

    @Test
    fun `when the file is not there then the result is null`() {
        assertNull(ResourceChangelogDataSource(folder.root, ConsoleLogger()).read())
    }

    @Test
    fun `when there is no resources folder then the result is null`() {
        assertNull(ResourceChangelogDataSource(null, ConsoleLogger()).read())
    }
}
