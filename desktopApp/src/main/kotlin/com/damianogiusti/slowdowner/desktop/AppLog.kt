package com.damianogiusti.slowdowner.desktop

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime

/**
 * Copies stdout and stderr to `logs/slowdowner.log`. On someone else's computer that file is the
 * only way to see what went wrong. The previous run stays as `slowdowner.previous.log`.
 */
object AppLog {
    /** A runaway loop must not fill the disk: past this the file stops growing. */
    private const val MAX_BYTES = 5L * 1024 * 1024

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
            val file = CappedOutputStream(FileOutputStream(current), MAX_BYTES)
            System.setOut(PrintStream(TeeOutputStream(System.out, file), true, Charsets.UTF_8))
            System.setErr(PrintStream(TeeOutputStream(System.err, file), true, Charsets.UTF_8))
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

private class CappedOutputStream(private val target: OutputStream, private val maxBytes: Long) : OutputStream() {
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

    override fun flush() = target.flush()
}
