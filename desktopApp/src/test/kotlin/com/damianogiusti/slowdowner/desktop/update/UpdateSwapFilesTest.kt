package com.damianogiusti.slowdowner.desktop.update

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateSwapFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir by lazy { folder.newFolder("install") }
    private val lockFile by lazy { File(dir, UpdateSwapFiles.LOCK) }
    private val toClose = mutableListOf<AutoCloseable>()
    private var holder: Process? = null

    @AfterTest
    fun cleanUp() {
        toClose.forEach { runCatching { it.close() } }
        holder?.destroyForcibly()?.waitFor()
    }

    // As the Windows script does: a lock on the first byte, held while it runs.
    private fun holdHere() {
        lockFile.writeText("2026-10-23T10:00:00")
        val file = RandomAccessFile(lockFile, "rw").also { toClose += it }
        file.channel.lock(0, 1, false)
    }

    @Test
    fun `when no update left a lock then no swap is running`() {
        assertFalse(UpdateSwapFiles.isSwapRunning(dir))
    }

    @Test
    fun `when the lock file is there but nobody holds it then no swap is running`() {
        lockFile.writeText("2026-10-23T10:00:00")

        assertFalse(UpdateSwapFiles.isSwapRunning(dir))
    }

    @Test
    fun `when another process holds a recent lock then a swap is running`() {
        lockFile.writeText("2026-10-23T10:00:00")
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), HoldUpdateLock::class.java.name, lockFile.path)
            .redirectErrorStream(true)
            .start()
            .also { holder = it }
        // The holder says when it has the lock.
        assertEquals(HoldUpdateLock.READY, process.inputStream.bufferedReader().readLine())

        assertTrue(UpdateSwapFiles.isSwapRunning(dir))
    }

    @Test
    fun `when the lock is held in this process then a swap is running`() {
        holdHere()

        assertTrue(UpdateSwapFiles.isSwapRunning(dir))
    }

    @Test
    fun `when the lock is older than five minutes then the app starts anyway`() {
        holdHere()

        assertFalse(UpdateSwapFiles.isSwapRunning(dir, now = Instant.now() + Duration.ofMinutes(6)))
    }

    @Test
    fun `when the lock has a date in the future then the app starts anyway`() {
        holdHere()

        assertFalse(UpdateSwapFiles.isSwapRunning(dir, now = Instant.now() - Duration.ofMinutes(1)))
    }

    @Test
    fun `the result is reported once`() {
        File(dir, UpdateSwapFiles.RESULT).writeText("replaced\r\n")

        assertEquals("replaced", UpdateSwapFiles.takeResult(dir))
        assertNull(UpdateSwapFiles.takeResult(dir))
    }

    @Test
    fun `when the result is not a known word then it is unknown`() {
        File(dir, UpdateSwapFiles.RESULT).writeText("C:\\Users\\anna\\something")

        assertEquals("unknown", UpdateSwapFiles.takeResult(dir))
    }

    @Test
    fun `when there was no swap then there is no result`() {
        assertNull(UpdateSwapFiles.takeResult(dir))
    }
}

/** The other process of the test above: holds the lock until it is killed. */
object HoldUpdateLock {
    const val READY = "locked"

    @JvmStatic
    fun main(args: Array<String>) {
        val file = RandomAccessFile(args[0], "rw")
        file.channel.lock(0, 1, false)
        println(READY)
        System.out.flush()
        Thread.sleep(TimeUnit.MINUTES.toMillis(1))
        exitProcess(0)
    }
}
