package com.damianogiusti.slowdowner.desktop

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeCrashesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val logs by lazy { folder.newFolder("logs") }
    private val temp by lazy { folder.newFolder("tmp") }
    private fun crashes() = NativeCrashes(logs, searchDirs = listOf(temp))

    @Test
    fun `when the previous run exited cleanly then nothing is collected`() {
        crashes().markRunning("1.0.1")
        crashes().markStopped()

        assertNull(crashes().collectPrevious())
    }

    @Test
    fun `when the previous run died without a report then nothing is collected`() {
        File(logs, "running.txt").writeText("4242\n1.0.1\n")

        assertNull(crashes().collectPrevious())
        assertFalse(File(logs, "running.txt").exists())
    }

    @Test
    fun `when the previous run crashed then its report moves to the logs`() {
        File(logs, "running.txt").writeText("4242\n1.0.1\n")
        File(temp, "hs_err_pid4242.log").writeText(REPORT)

        val crash = assertNotNull(crashes().collectPrevious())

        assertEquals("1.0.1", crash.version)
        assertEquals("SIGSEGV (0xb)", crash.excerpt.signal)
        assertEquals("C  [libsdengine.dylib+0x1a2b]  sd_engine_render+0x40", crash.excerpt.problematicFrame)
        assertEquals(REPORT, File(logs, "native-crash.log").readText())
        assertFalse(File(temp, "hs_err_pid4242.log").exists())
        assertNull(crashes().collectPrevious())
    }

    @Test
    fun `a report from another process is left alone`() {
        File(logs, "running.txt").writeText("4242\n1.0.1\n")
        File(temp, "hs_err_pid7.log").writeText(REPORT)

        assertNull(crashes().collectPrevious())
        assertTrue(File(temp, "hs_err_pid7.log").exists())
    }

    @Test
    fun `a report of another app that got the same pid is left alone`() {
        File(logs, "running.txt").writeText("4242\n1.0.1\n")
        File(temp, "hs_err_pid4242.log").writeText("#  SIGSEGV (0xb) at pc=0x1, pid=4242\nCommand Line: org.example.OtherApp\n")

        assertNull(crashes().collectPrevious())
        assertTrue(File(temp, "hs_err_pid4242.log").exists())
    }

    @Test
    fun `the excerpt keeps the stacks and drops the machine details`() {
        val text = NativeCrashes.excerpt(REPORT.lines()).text

        assertTrue("SIGSEGV" in text)
        assertTrue("Current thread" in text)
        assertTrue("C  [libsdengine.dylib+0x1a2b]  sd_engine_render+0x40" in text)
        assertTrue("j  com.damianogiusti.slowdowner.data.playback.NativeEngine.render" in text)
        assertFalse("USER=anna" in text)
        assertFalse("Command Line" in text)
        assertFalse("Host:" in text)
    }

    @Test
    fun `the home folder is replaced with a tilde`() {
        assertEquals(
            "Cannot read ~/Music/Take 3.mp3",
            scrubHome("Cannot read /Users/anna/Music/Take 3.mp3", home = "/Users/anna"),
        )
        assertEquals("C:\\nothing", scrubHome("C:\\nothing", home = ""))
    }

    @Test
    fun `a Windows home is replaced in every form`() {
        val home = "C:\\Users\\Anna"

        assertEquals("~\\Music\\a.mp3", scrubHome("C:\\Users\\Anna\\Music\\a.mp3", home))
        assertEquals("file:///~/Music/a.mp3", scrubHome("file:///c:/Users/anna/Music/a.mp3", home))
    }

    @Test
    fun `a link keeps only the video`() {
        assertEquals("https://www.youtube.com/watch?v=abc", publicLink("https://www.youtube.com/watch?v=abc&list=PL1&si=x"))
        assertEquals("https://youtu.be/abc", publicLink("https://youtu.be/abc?si=x"))
        assertEquals("https://www.youtube.com/shorts/abc", publicLink("https://www.youtube.com/shorts/abc"))
    }

    private companion object {
        // Shortened from a real HotSpot 23 report.
        val REPORT = """
            #
            # A fatal error has been detected by the Java Runtime Environment:
            #
            #  SIGSEGV (0xb) at pc=0x00000001090ebae0, pid=4242, tid=8963
            #
            # JRE version: OpenJDK Runtime Environment (23.0.1+11) (build 23.0.1+11)
            # Problematic frame:
            # C  [libsdengine.dylib+0x1a2b]  sd_engine_render+0x40
            #

            ---------------  S U M M A R Y ------------

            Command Line: --enable-native-access=ALL-UNNAMED -Dslowdowner.version=1.0.1 com.damianogiusti.slowdowner.desktop.MainKt

            Host: "MacBookPro18,1" arm64, 10 cores, 32G, Darwin 25.6.0

            ---------------  T H R E A D  ---------------

            Current thread (0x00000001032fdc80):  JavaThread "AWT-EventQueue-0"  [_thread_in_native, id=8963]

            Stack: [0x000000016d110000,0x000000016d313000],  sp=0x000000016d3122f0,  free space=2056k
            Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
            C  [libsdengine.dylib+0x1a2b]  sd_engine_render+0x40
            j  com.damianogiusti.slowdowner.data.playback.NativeEngine.render()V+12
            Java frames: (J=compiled Java code, j=interpreted, Vv=VM code)
            j  com.damianogiusti.slowdowner.data.playback.NativeEngine.render()V+12

            ---------------  S Y S T E M  ---------------

            Environment Variables:
            USER=anna
            HOME=/Users/anna
        """.trimIndent()
    }
}
