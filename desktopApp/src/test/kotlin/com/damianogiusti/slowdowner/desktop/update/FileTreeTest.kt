package com.damianogiusti.slowdowner.desktop.update

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FileTreeTest {

    private val root = Files.createTempDirectory("downstep-tree").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteTree()
    }

    @Test
    fun `when a folder holds a link then the link goes and its target stays`() {
        val outside = File(root, "outside").apply { mkdirs() }
        val keep = File(outside, "keep.txt").apply { writeText("keep") }
        val tree = File(root, "tree/nested").apply { mkdirs() }
        File(tree, "file.txt").writeText("x")
        Files.createSymbolicLink(File(tree, "link").toPath(), outside.toPath())

        File(root, "tree").deleteTree()

        assertFalse(File(root, "tree").exists())
        assertEquals("keep", keep.readText())
    }

    @Test
    fun `when the folder does not exist then nothing happens`() {
        File(root, "missing").deleteTree()
    }

    @Test
    fun `when a delete is refused and the entry was read-only then the flag goes and the delete is tried again`() {
        val attempts = mutableListOf<Path>()
        val cleared = mutableListOf<Path>()
        val path = File(root, "entry").toPath()

        deleteClearingReadOnly(
            path,
            delete = { attempts.add(it); if (attempts.size == 1) throw AccessDeniedException(it.toString()) },
            clearReadOnly = { cleared.add(it); true },
        )

        assertEquals(listOf(path, path), attempts)
        assertEquals(listOf(path), cleared)
    }

    @Test
    fun `when a delete is refused and the entry was not read-only then the refusal is thrown without a second try`() {
        var attempts = 0
        val path = File(root, "entry").toPath()

        assertFailsWith<AccessDeniedException> {
            deleteClearingReadOnly(path, delete = { attempts++; throw AccessDeniedException(it.toString()) }, clearReadOnly = { false })
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `on Windows a read-only folder in the tree goes too`() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val nested = File(root, "tree/nested").apply { mkdirs() }
        File(nested, "file.txt").writeText("x")
        Files.setAttribute(nested.toPath(), "dos:readonly", true, LinkOption.NOFOLLOW_LINKS)

        File(root, "tree").deleteTree()

        assertFalse(File(root, "tree").exists())
    }
}
