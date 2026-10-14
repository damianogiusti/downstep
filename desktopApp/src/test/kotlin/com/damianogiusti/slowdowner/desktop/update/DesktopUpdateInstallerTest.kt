package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.desktop.update.DesktopUpdateInstaller.Companion.run
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import org.junit.Assume.assumeTrue
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
import kotlin.test.assertTrue

/**
 * The real swap, with the real scripts, on a fake app: a few files that look like a jpackage app,
 * with a launcher that does nothing. Each test runs on its own OS.
 */
class DesktopUpdateInstallerTest {

    private val root = Files.createTempDirectory("downstep-update").toFile().canonicalFile
    private val workDir = File(root, "work")
    private val started = File(root, "started.txt")
    private val waiting = mutableListOf<Process>()

    @AfterTest
    fun tearDown() {
        waiting.forEach { it.destroyForcibly() }
        // The swap script can still hold its log for a moment after the last check (Windows locks it).
        val deadline = System.currentTimeMillis() + 30_000
        while (true) {
            try {
                root.deleteTree()
                return
            } catch (e: java.io.IOException) {
                if (System.currentTimeMillis() > deadline) throw e
                Thread.sleep(500)
            }
        }
    }

    @Test
    fun `on macOS the new app from the disk image replaces the old one after the app quits, and opens`() {
        assumeTrue(isMac)
        val installed = macApp(File(root, "Applications/Downstep.app"), "1.0.3")
        val dmg = macDiskImage("1.0.4")
        val app = sleepingProcess()

        DesktopUpdateInstaller(AppInstallation.MacBundle(installed), workDir, ConsoleLogger(), app.pid()).prepare(dmg, "1.0.4")
        // The swap waits for the app to quit.
        Thread.sleep(1_000)
        assertEquals("1.0.3", versionOf(File(installed, "Contents/app")))
        app.destroy()

        waitFor { started.exists() }
        // The new launcher wrote it: the opened app is the new one.
        assertEquals("1.0.4", started.readText().trim())
        assertEquals("1.0.4", versionOf(File(installed, "Contents/app")))
        assertFalse(File(installed.parentFile, ".Downstep.app.old").exists())
        assertFalse(File(workDir, "Downstep.app").exists())
        run("codesign", "--verify", "--deep", "--strict", installed.path)
        assertFalse("com.apple.quarantine" in run("xattr", "-r", installed.path))
    }

    @Test
    fun `on macOS a disk image with another version changes nothing`() {
        assumeTrue(isMac)
        val installed = macApp(File(root, "Applications/Downstep.app"), "1.0.3")
        val dmg = macDiskImage("1.0.5")

        assertFailsWith<IOException> {
            DesktopUpdateInstaller(AppInstallation.MacBundle(installed), workDir, ConsoleLogger(), sleepingProcess().pid()).prepare(dmg, "1.0.4")
        }

        assertFalse(File(workDir, "replace-app.sh").exists())
        assertEquals("1.0.3", versionOf(File(installed, "Contents/app")))
    }

    @Test
    fun `on Windows the new folder from the zip replaces the old one after the app quits, and starts`() {
        assumeTrue(isWindows)
        val installed = windowsApp(File(root, "Apps/Downstep"), "1.0.3")
        val zip = windowsZip("1.0.4")
        val app = sleepingProcess()

        DesktopUpdateInstaller(AppInstallation.WindowsFolder(installed), workDir, ConsoleLogger(), app.pid()).prepare(zip, "1.0.4")
        Thread.sleep(1_000)
        assertEquals("1.0.3", versionOf(File(installed, "app")))
        app.destroy()

        waitFor { "Started" in scriptLog() }
        waitFor { !File(installed.parentFile, "Downstep.old").exists() }
        assertEquals("1.0.4", versionOf(File(installed, "app")))
        assertFalse(File(installed.parentFile, "Downstep.update").exists())
    }

    @Test
    fun `on Windows a zip with another app changes nothing`() {
        assumeTrue(isWindows)
        val installed = windowsApp(File(root, "Apps/Downstep"), "1.0.3")
        val zip = windowsZip("1.0.4", mainClass = "com.example.Other")

        assertFailsWith<IOException> {
            DesktopUpdateInstaller(AppInstallation.WindowsFolder(installed), workDir, ConsoleLogger(), sleepingProcess().pid()).prepare(zip, "1.0.4")
        }

        assertEquals("1.0.3", versionOf(File(installed, "app")))
    }

    private fun macApp(bundle: File, version: String): File {
        writeConfig(File(bundle, "Contents/app"), version)
        File(bundle, "Contents/Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0"><dict>
              <key>CFBundleExecutable</key><string>Downstep</string>
              <key>CFBundleIdentifier</key><string>com.damianogiusti.slowdowner.updatetest</string>
              <key>CFBundlePackageType</key><string>APPL</string>
              <key>CFBundleShortVersionString</key><string>$version</string>
            </dict></plist>
            """.trimIndent(),
        )
        File(bundle, "Contents/MacOS/Downstep").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\necho $version > '${started.path}'\n")
            setExecutable(true)
        }
        // Ad hoc, like the release.
        run("codesign", "--force", "--deep", "--sign", "-", bundle.path)
        return bundle
    }

    private fun macDiskImage(version: String): File {
        val content = File(root, "dmg-content-$version")
        macApp(File(content, "Downstep.app"), version)
        // Like the release image, with a link next to the app. Never to the real /Applications in a test.
        val applications = File(root, "fake-applications").apply { mkdirs() }
        Files.createSymbolicLink(File(content, "Applications").toPath(), applications.toPath())
        // An empty image filled by hand: "hdiutil create -srcfolder" fails with "Resource busy" on some Macs.
        val blank = File(root, "blank-$version.dmg")
        run("hdiutil", "create", "-size", "20m", "-fs", "HFS+", "-volname", "Downstep", "-ov", blank.path)
        val mountPoint = File(root, "mount-$version").apply { mkdirs() }
        run("hdiutil", "attach", "-nobrowse", "-noautoopen", "-mountpoint", mountPoint.path, blank.path)
        try {
            run("ditto", content.path, mountPoint.path)
        } finally {
            run("hdiutil", "detach", mountPoint.path, "-force")
        }
        val dmg = File(root, "Downstep-mac.dmg")
        run("hdiutil", "convert", blank.path, "-format", "UDZO", "-ov", "-o", dmg.path)
        return dmg
    }

    private fun windowsApp(folder: File, version: String): File {
        writeConfig(File(folder, "app"), version)
        launcherExe().copyTo(File(folder, "Downstep.exe"))
        return folder
    }

    private fun windowsZip(version: String, mainClass: String = MAIN_CLASS): File {
        val zip = File(root, "Downstep-windows-x64.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            fun entry(name: String, bytes: ByteArray) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
            entry("Downstep/Downstep.exe", launcherExe().readBytes())
            entry("Downstep/app/Downstep.cfg", config(version, mainClass).toByteArray())
            entry("licenses/LICENSE.txt", "GPL".toByteArray())
        }
        return zip
    }

    /** A stand-in for Downstep.exe that runs and quits at once: whoami. The script log tells it started. */
    private fun launcherExe(): File {
        val exe = File(root, "launcher/Downstep.exe")
        if (!exe.exists()) File(System.getenv("SystemRoot"), "System32/whoami.exe").copyTo(exe)
        return exe
    }

    private fun writeConfig(appDir: File, version: String) {
        appDir.mkdirs()
        File(appDir, "Downstep.cfg").writeText(config(version, MAIN_CLASS))
    }

    private fun config(version: String, mainClass: String) =
        "[Application]\napp.mainclass=$mainClass\n\n[JavaOptions]\njava-options=-Dslowdowner.version=$version\n"

    private fun versionOf(appDir: File) =
        File(appDir, "Downstep.cfg").readLines().first { it.startsWith("java-options=-Dslowdowner.version=") }.substringAfterLast('=')

    /** Plays the running app: the swap waits for it to end. */
    private fun sleepingProcess(): Process {
        val command = if (isWindows) listOf("powershell.exe", "-NoProfile", "-Command", "Start-Sleep -Seconds 60")
        else listOf("sleep", "60")
        return ProcessBuilder(command).start().also { waiting += it }
    }

    private fun scriptLog() = File(workDir, "replace-app.log").takeIf { it.exists() }?.readText().orEmpty()

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 60_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out. Script log:\n${scriptLog()}")
            }
            Thread.sleep(200)
        }
    }

    private companion object {
        const val MAIN_CLASS = "com.damianogiusti.slowdowner.desktop.MainKt"
        val isMac = System.getProperty("os.name").startsWith("Mac")
        val isWindows = System.getProperty("os.name").startsWith("Windows")
    }
}
