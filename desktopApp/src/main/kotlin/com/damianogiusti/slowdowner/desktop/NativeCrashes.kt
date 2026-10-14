package com.damianogiusti.slowdowner.desktop

import java.io.File

/**
 * A crash in the C engine kills the JVM before any Java handler runs. The JVM still writes an
 * hs_err_pid<pid>.log: in the working directory, or in the temp folder when that is not writable
 * (a Mac app opened from the Finder runs in "/"). The path cannot be set at build time, so each run
 * leaves its pid in a marker and deletes it on a clean exit. A marker found at start means the
 * previous run died: its report, if any, is moved next to the log.
 */
class NativeCrashes(
    private val logs: File,
    private val searchDirs: List<File> = defaultSearchDirs(),
) {
    // One app instance at a time is assumed: a second one would share this marker.
    private val marker get() = File(logs, "running.txt")

    /** The previous run's crash, moved to `logs/native-crash.log`; null when it ended cleanly. */
    fun collectPrevious(): NativeCrash? {
        val lines = marker.takeIf { it.exists() }?.readLines() ?: return null
        marker.delete()
        val pid = lines.getOrNull(0)?.trim() ?: return null
        val version = lines.getOrNull(1)?.trim()
        // After a kill or a power cut the pid may have gone to another Java app that crashed later.
        val report = searchDirs.map { File(it, "hs_err_pid$pid.log") }
            .firstOrNull { it.isFile && it.useLines { content -> content.any { line -> "slowdowner" in line } } }
            ?: return null
        val kept = File(logs, "native-crash.log")
        kept.delete()
        // The full report stays on this computer: only an excerpt is sent.
        if (!report.renameTo(kept)) report.copyTo(kept, overwrite = true).also { report.delete() }
        return NativeCrash(version, excerpt(kept.readLines()))
    }

    fun markRunning(version: String) {
        logs.mkdirs()
        marker.writeText("${ProcessHandle.current().pid()}\n$version\n")
    }

    fun markStopped() {
        marker.delete()
    }

    companion object {
        private fun defaultSearchDirs(): List<File> =
            listOfNotNull(System.getProperty("user.dir"), System.getProperty("java.io.tmpdir"), "/tmp")
                .map(::File)
                .distinct()

        /**
         * The parts that say what crashed: the header, the crashed thread and its stacks. The
         * rest of the report lists environment variables, the command line and the machine.
         */
        fun excerpt(report: List<String>): NativeCrashExcerpt {
            val header = report.takeWhile { it.startsWith("#") }
            val frameIndex = header.indexOfFirst { it.startsWith("# Problematic frame:") }
            val problematicFrame = if (frameIndex < 0) null else header.getOrNull(frameIndex + 1)?.removePrefix("#")?.trim()
            // "#  SIGSEGV (0xb) at pc=0x..., pid=..., tid=..."
            val signal = header.firstOrNull { " at pc=" in it }?.removePrefix("#")?.trim()?.substringBefore(" at pc=")
            val thread = report.firstOrNull { it.startsWith("Current thread") }
            // Each stack ends at a blank line; the native one runs straight into the Java one.
            val titles = listOf("Native frames:", "Java frames:")
            val stacks = titles.flatMap { title ->
                val start = report.indexOfFirst { it.startsWith(title) }
                if (start < 0) return@flatMap emptyList()
                listOf(report[start]) + report.drop(start + 1)
                    .takeWhile { line -> line.isNotBlank() && titles.none { line.startsWith(it) } } + ""
            }
            val text = (header + "" + listOfNotNull(thread) + "" + stacks).joinToString("\n")
            return NativeCrashExcerpt(signal, problematicFrame, text)
        }
    }
}

/** [version] is the one that crashed, which may not be the one running now. */
data class NativeCrash(val version: String?, val excerpt: NativeCrashExcerpt)

data class NativeCrashExcerpt(val signal: String?, val problematicFrame: String?, val text: String)
