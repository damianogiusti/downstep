package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// No script runs here: these only check what happens around it, on any computer.
class DesktopUpdateInstallerStateTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val workDir by lazy { folder.newFolder("install") }
    private val now = System.currentTimeMillis()

    @Test
    fun `when nothing is prepared then the swap does not start and the app must not quit`() {
        val installer = DesktopUpdateInstaller(AppInstallation.WindowsFolder(folder.newFolder("Downstep")), workDir, ConsoleLogger())

        assertFailsWith<IllegalStateException> { installer.startSwap() }
        assertFalse(File(workDir, "replace-app.log").exists())
    }

    @Test
    fun `when a Windows update was prepared over an hour ago and never swapped then it goes`() {
        val installed = folder.newFolder("Apps", "Downstep")
        val staged = staged(File(installed.parentFile, "Downstep.update"), ageMillis = TWO_HOURS)

        DesktopUpdateInstaller.discardAbandoned(AppInstallation.WindowsFolder(installed), workDir, now)

        assertFalse(staged.exists())
        assertTrue(installed.exists())
        // Renamed before it was deleted: nothing of it is left under either name.
        assertEquals(listOf("Downstep"), installed.parentFile.list()?.toList())
    }

    @Test
    fun `when the Windows update was prepared recently then it stays, a swap may be moving it`() {
        val installed = folder.newFolder("Apps", "Downstep")
        val staged = staged(File(installed.parentFile, "Downstep.update"), ageMillis = TEN_MINUTES)

        DesktopUpdateInstaller.discardAbandoned(AppInstallation.WindowsFolder(installed), workDir, now)

        assertTrue(staged.exists())
    }

    @Test
    fun `on macOS a prepared update stays, there is no lock to rule out a swap`() {
        val bundle = folder.newFolder("Applications", "Downstep.app")
        val staged = staged(File(workDir, "Downstep.app"), ageMillis = TWO_HOURS)

        DesktopUpdateInstaller.discardAbandoned(AppInstallation.MacBundle(bundle), workDir, now)

        assertTrue(staged.exists())
    }

    private fun staged(dir: File, ageMillis: Long): File {
        File(dir, "app").mkdirs()
        File(dir, "app/Downstep.cfg").writeText("app.mainclass=x")
        dir.setLastModified(now - ageMillis)
        return dir
    }

    private companion object {
        const val TEN_MINUTES = 10 * 60 * 1000L
        const val TWO_HOURS = 2 * 60 * 60 * 1000L
    }
}
