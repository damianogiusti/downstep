package com.damianogiusti.slowdowner.desktop

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

/**
 * Copies stdout and stderr to `logs/slowdowner.log`. On someone else's computer that file is the
 * only way to see what went wrong. The previous run stays as `slowdowner.previous.log`.
 *
 * Writes are buffered: stdout lines reach the disk within [FLUSH_INTERVAL_MS], stderr lines (errors,
 * stack traces) at once, and whatever is left when the JVM shuts down. A native crash skips the
 * shutdown hooks: the interval bounds what it can lose.
 */
object AppLog {
    /** A runaway loop must not fill the disk: past this the file stops growing. */
    private const val MAX_BYTES = 5L * 1024 * 1024
    private const val FLUSH_INTERVAL_MS = 1_000L

    fun start(directory: File, version: String) {
        // Without a log the app still works: never fail the start for it.
        runCatching {
            directory.mkdirs()
            val current = File(directory, "slowdowner.log")
            val previous = File(directory, "slowdowner.previous.log")
            if (current.exists()) {
                previous.delete()
                current.renameTo(previous)
            }
            val file = LogFile(current, MAX_BYTES)
            // PrintStream flushes at every line: on stdout that would undo the buffer.
            System.setOut(PrintStream(TeeOutputStream(System.out, file.flushedLater()), true, Charsets.UTF_8))
            System.setErr(PrintStream(TeeOutputStream(System.err, file), true, Charsets.UTF_8))
            fixedRateTimer("AppLog flush", daemon = true, period = FLUSH_INTERVAL_MS) { file.flush() }
            Runtime.getRuntime().addShutdownHook(Thread(file::flush, "AppLog shutdown"))
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                System.err.println("Uncaught exception on ${thread.name}")
                error.printStackTrace()
            }
            println(
                "Downstep $version, ${LocalDateTime.now()}, " +
                    "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}, " +
                    "Java ${System.getProperty("java.version")}",
            )
        }.onFailure { System.err.println("Cannot write the log: $it") }
    }
}

private class TeeOutputStream(private val first: OutputStream, private val second: OutputStream) : OutputStream() {
    override fun write(b: Int) {
        first.write(b)
        second.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        first.write(b, off, len)
        second.write(b, off, len)
    }

    override fun flush() {
        first.flush()
        second.flush()
    }
}

/** The log file, buffered and capped. Shared by stdout and stderr, hence synchronized. */
internal class LogFile(file: File, private val maxBytes: Long) : OutputStream() {
    private val target = BufferedOutputStream(FileOutputStream(file), BUFFER_BYTES)
    private var written = 0L

    @Synchronized
    override fun write(b: Int) {
        if (written >= maxBytes) return
        target.write(b)
        written++
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val allowed = minOf(len.toLong(), maxBytes - written).toInt()
        if (allowed <= 0) return
        target.write(b, off, allowed)
        written += allowed
    }

    @Synchronized
    override fun flush() = target.flush()

    @Synchronized
    override fun close() = target.close()

    /** The same file, for a stream whose flushes should wait for the timer. */
    fun flushedLater(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = this@LogFile.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = this@LogFile.write(b, off, len)
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
    }
}
