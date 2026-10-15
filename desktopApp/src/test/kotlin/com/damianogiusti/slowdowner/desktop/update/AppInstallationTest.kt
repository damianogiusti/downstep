package com.damianogiusti.slowdowner.desktop.update

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppInstallationTest {

    private val root = Files.createTempDirectory("downstep-installation").toFile()

    @AfterTest
    fun tearDown() {
        root.setWritable(true)
        root.walk().forEach { it.setWritable(true) }
        root.deleteTree()
    }

    @Test
    fun `when the launcher is inside an app bundle then the bundle is the installation`() {
        val bundle = File(root, "Downstep.app")
        val launcher = File(bundle, "Contents/MacOS/Downstep").apply { parentFile.mkdirs() }

        assertEquals(AppInstallation.MacBundle(bundle), AppInstallation.macBundle(launcher))
    }

    @Test
    fun `when macOS runs a moved copy then the app cannot replace itself`() {
        assumeTrue(isMac)
        val bundle = File(root, "AppTranslocation/1234/d/Downstep.app")
        val launcher = File(bundle, "Contents/MacOS/Downstep").apply { parentFile.mkdirs() }

        assertNull(AppInstallation.macBundle(launcher))
    }

    @Test
    fun `when the launcher is not in an app bundle then there is no installation`() {
        val launcher = File(root, "build/bin/Downstep").apply { parentFile.mkdirs() }

        assertNull(AppInstallation.macBundle(launcher))
    }

    @Test
    fun `when the folder around the app is read only then the app cannot replace itself`() {
        // POSIX permissions: on Windows the check is a real write, and a read-only flag means something else.
        assumeTrue(isMac)
        val applications = File(root, "Applications")
        val bundle = File(applications, "Downstep.app")
        val launcher = File(bundle, "Contents/MacOS/Downstep").apply { parentFile.mkdirs() }
        applications.setWritable(false)

        assertNull(AppInstallation.macBundle(launcher))
    }

    @Test
    fun `when the exe is in a writable folder then the folder is the installation`() {
        val folder = File(root, "Downstep")
        val launcher = File(folder, "Downstep.exe").apply { parentFile.mkdirs() }

        assertEquals(AppInstallation.WindowsFolder(folder), AppInstallation.windowsFolder(launcher))
    }

    @Test
    fun `when the exe was renamed then the app cannot replace itself`() {
        val launcher = File(root, "Downstep/MyPlayer.exe").apply { parentFile.mkdirs() }

        assertNull(AppInstallation.windowsFolder(launcher))
    }

    private companion object {
        val isMac = System.getProperty("os.name").startsWith("Mac")
    }
}
