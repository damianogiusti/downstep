package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What actually leaves the computer: the SDK talks to a local server instead of Sentry. */
class CrashReportingTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val received: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            val gzip = exchange.requestHeaders.getFirst("Content-Encoding") == "gzip"
            received += String(if (gzip) GZIPInputStream(body.inputStream()).readBytes() else body)
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        start()
    }

    @After
    fun tearDown() {
        // Sentry is static: other tests in this JVM must not report.
        CrashReporting.stop()
        System.clearProperty("slowdowner.sentryDsn")
        server.stop(0)
    }

    @Test
    fun `without a DSN the logger only prints`() {
        CrashReporting.start("1.0.2", AppDirectories(folder.newFolder("data")))

        assertTrue(CrashReporting.logger() is ConsoleLogger)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `the installation id survives a restart`() {
        val directories = AppDirectories(folder.newFolder("data"))
        System.setProperty("slowdowner.sentryDsn", "http://public@127.0.0.1:${server.address.port}/1")

        CrashReporting.start("1.0.2", directories)
        CrashReporting.stop()
        val first = File(directories.root, "installation-id").readText()
        CrashReporting.start("1.0.2", directories)
        CrashReporting.stop()

        assertEquals(first, File(directories.root, "installation-id").readText())
    }

    @Test
    fun `reports and logs carry the import link but not the home folder or the computer name`() {
        val directories = AppDirectories(folder.newFolder("data"))
        // A native crash in the previous run, as NativeCrashes finds it.
        File(directories.logs.apply { mkdirs() }, "running.txt").writeText("4242\n1.0.1\n")
        val tmp = File(System.getProperty("java.io.tmpdir"), "hs_err_pid4242.log")
        tmp.writeText(
            "#  SIGSEGV (0xb) at pc=0x1, pid=4242, tid=1\n# Problematic frame:\n# C  [libsdengine.dylib+0x1a2b]  render+0x40\n" +
                "\nCommand Line: -Dslowdowner.version=1.0.1 com.damianogiusti.slowdowner.desktop.MainKt\n",
        )
        System.setProperty("slowdowner.sentryDsn", "http://public@127.0.0.1:${server.address.port}/1")
        val home = System.getProperty("user.home")

        try {
            CrashReporting.start("1.0.2", directories)
            val logger = CrashReporting.logger()
            logger.d("Only for development")
            logger.i("App started", mapOf("engine" to "ok"))
            logger.w("Cannot save the export", IOException("No space in $home/Music"))
            logger.e("Restore failed", IllegalStateException("/Volumes/Band/Autumn Leaves.mp3 (Permission denied)"))
            logger.e(
                "Import failed",
                IllegalStateException("Cannot parse $home/Library/Caches/page.html"),
                mapOf("source" to "youtube", "url" to "https://www.youtube.com/watch?v=abc"),
            )
            CrashReporting.stop()
        } finally {
            tmp.delete()
        }

        val all = received.joinToString("\n")
        val installation = File(directories.root, "installation-id").readText()
        assertTrue("https://www.youtube.com/watch?v=abc" in all)
        assertFalse("-Dslowdowner.version" in all)
        assertTrue("Cannot parse <path>" in all)
        assertTrue("<path> (Permission denied)" in all)
        assertFalse("Autumn Leaves" in all)
        // Sentry Logs: info and warnings, scrubbed; debug stays on the computer.
        assertTrue("App started" in all)
        // A log line keeps only the type of the cause: its message could name a file.
        assertTrue("java.io.IOException" in all)
        assertFalse("No space in" in all)
        assertFalse("Only for development" in all)
        // One random id for the user and the session.
        assertTrue(""""id":"$installation"""" in all)
        assertTrue(""""did":"$installation"""" in all)
        assertFalse(home in all)
        assertFalse("server_name" in all)
        assertTrue("Native crash: SIGSEGV (0xb) in C  [libsdengine.dylib+0x1a2b]  render+0x40" in all)
        assertTrue("downstep@1.0.1" in all)
        assertTrue("hs_err-excerpt.txt" in all)
        assertFalse(File(directories.logs, "running.txt").exists())
    }
}
