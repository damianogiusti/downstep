package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
