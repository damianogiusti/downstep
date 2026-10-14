package com.damianogiusti.slowdowner.desktop.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ZipArchiveTest {

    private val root = Files.createTempDirectory("downstep-zip").toFile()
    private val target = File(root, "Downstep.update")

    @AfterTest
    fun tearDown() {
        root.deleteTree()
    }

    @Test
    fun `when the zip is a release then only the app folder is unpacked`() {
        val zip = zip("Downstep/Downstep.exe" to "exe", "Downstep/app/Downstep.cfg" to "cfg", "licenses/LICENSE.txt" to "gpl")

        ZipArchive.extractFolder(zip, "Downstep", target)

        assertEquals("exe", File(target, "Downstep.exe").readText())
        assertEquals("cfg", File(target, "app/Downstep.cfg").readText())
        assertFalse(File(target, "LICENSE.txt").exists())
    }

    @Test
    fun `when entries use backslashes then they are unpacked as folders`() {
        val zip = zip("Downstep\\app\\Downstep.cfg" to "cfg")

        ZipArchive.extractFolder(zip, "Downstep", target)

        assertEquals("cfg", File(target, "app/Downstep.cfg").readText())
    }

    @Test
    fun `when an entry points outside the folder then nothing escapes`() {
        val zip = zip("Downstep/../../evil.txt" to "evil")

        assertFailsWith<IOException> { ZipArchive.extractFolder(zip, "Downstep", target) }

        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(root.parentFile, "evil.txt").exists())
    }

    @Test
    fun `when the zip has no app folder then it fails`() {
        val zip = zip("Other/Other.exe" to "exe")

        assertFailsWith<IOException> { ZipArchive.extractFolder(zip, "Downstep", target) }
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File(root, "release.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
        return file
    }
}
