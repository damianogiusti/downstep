package com.damianogiusti.slowdowner.desktop

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlin.test.Test
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
    fun `reports carry the import link but not the home folder or the computer name`() {
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
            CrashReporting.reportImportFailure(
                "https://www.youtube.com/watch?v=abc&list=PLprivate&si=token",
                IllegalStateException("Cannot parse $home/Library/Caches/page.html"),
            )
            CrashReporting.stop()
        } finally {
            tmp.delete()
        }

        val all = received.joinToString("\n")
        assertTrue("https://www.youtube.com/watch?v=abc" in all)
        assertFalse("PLprivate" in all)
        assertFalse("-Dslowdowner.version" in all)
        assertTrue("Cannot parse ~/Library/Caches/page.html" in all)
        assertFalse(home in all)
        assertFalse("server_name" in all)
        assertTrue("Native crash: SIGSEGV (0xb) in C  [libsdengine.dylib+0x1a2b]  render+0x40" in all)
        assertTrue("downstep@1.0.1" in all)
        assertTrue("hs_err-excerpt.txt" in all)
        assertFalse(File(directories.logs, "running.txt").exists())
    }
}
