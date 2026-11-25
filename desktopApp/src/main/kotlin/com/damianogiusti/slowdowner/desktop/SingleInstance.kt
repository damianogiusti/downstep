package com.damianogiusti.slowdowner.desktop

import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * One copy of the app per data folder: two copies would share the database and the audio device, and
 * quitting one freed the engine under the other's feet. On Windows people start the app again while
 * the first copy is still opening. The later copy asks the first one to come forward, then quits.
 *
 * The rule: a launch gives up when another copy answers OPEN (its window came forward), or when the
 * copy holding the lock took it less than [YOUNG_OWNER] ago and is not quitting: that copy is still
 * starting and its window will come. On a slow PC people click again and again while nothing shows,
 * and each click used to become a window of its own (Sentry: bursts of 4-6 copies). The age comes from
 * the lock file's date, which the holder sets when it takes the lock: it is there even when the socket
 * is not, or does not answer yet.
 *
 * Past that age, a copy that is still starting, frozen, quitting, silent or that cannot be checked
 * never keeps the app closed: the launch keeps asking and trying the lock for a while, then starts
 * anyway. At worst two copies run, as before this check. The other answers (STARTING, QUITTING, FROZEN)
 * only tell why: the launch remembers the last one and reports it when it starts anyway.
 *
 * The lock is the operating system's: it goes away with the process, even after a crash. The call
 * goes through a Unix domain socket next to it, which, unlike a TCP port, raises no firewall prompt.
 */
class SingleInstance private constructor(
    private val channel: FileChannel?,
    private val lock: FileLock?,
    private val server: ServerSocketChannel?,
    private val socket: File,
) : AutoCloseable {

    /**
     * Called on the listener thread when another copy starts: true if the window came forward, and
     * only then the other copy quits. Null until the window exists.
     */
    @Volatile
    var onAnotherLaunch: (() -> Boolean)? = null

    @Volatile
    private var isQuitting = false

    init {
        server?.let { listening ->
            thread(isDaemon = true, name = "single-instance") { accept(listening) }
        }
    }

    /**
     * The launches that quit while this copy was starting, counting on its window, as log properties
     * ("while", "launches", "answers", "waited"); null if there were none. They cannot log themselves:
     * they quit before crash reporting starts. Each one left a mark next to the lock, read and removed
     * here. Called once the window exists, which then comes forward for them.
     */
    fun takeJoinedLaunches(): Map<String, String>? {
        val marks = File(socket.parentFile, JOINED_DIR).listFiles().orEmpty().filter { it.isFile }
        marks.forEach { it.delete() }
        val launches = marks.mapNotNull { JoinedLaunch.parse(it.name) }
        if (launches.isEmpty()) return null
        return mapOf(
            "while" to "starting",
            "launches" to countBucket(launches.size),
            "answers" to launches.map { it.answer }.distinct().sorted().joinToString(","),
            "waited" to launches.maxBy { WAIT_BUCKETS.indexOf(it.waited) }.waited,
        )
    }

    /** The window is gone: a new launch must not count on this copy and waits for it to go instead. */
    fun quitting() {
        isQuitting = true
        onAnotherLaunch = null
    }

    // Nothing may stop this loop but close(): a dead listener would leave every later launch waiting.
    private fun accept(listening: ServerSocketChannel) {
        while (listening.isOpen) {
            val client = runCatching { listening.accept() }.getOrNull()
            if (client == null) {
                Thread.sleep(ACCEPT_RETRY.inWholeMilliseconds)
                continue
            }
            client.use {
                runCatching { it.write(ByteBuffer.wrap(byteArrayOf(answer()))) }
            }
        }
    }

    private fun answer(): Byte {
        if (isQuitting) return QUITTING
        val comeForward = onAnotherLaunch ?: return STARTING
        return if (runCatching { comeForward() }.getOrDefault(false)) OPEN else FROZEN
    }

    override fun close() {
        runCatching { server?.close() }
        if (server != null) socket.delete()
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }

    companion object {
        private const val OPEN: Byte = 1
        private const val STARTING: Byte = 2
        private const val QUITTING: Byte = 3
        private const val FROZEN: Byte = 4

        // Not on the wire: no socket file, a socket file nobody accepts on, or a reply that did not come.
        private const val NO_SOCKET: Byte = 0
        private const val SILENT: Byte = -1
        private const val REFUSED: Byte = -2
        private const val JOINED_DIR = "launches"
        private val RETRY = 300.milliseconds
        private val YOUNG_OWNER = 60.seconds
        private val IS_WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")
        private val ACCEPT_RETRY = 100.milliseconds

        /**
         * Null when another copy answered that its window came forward, or is still starting (see the
         * class comment): this launch should quit.
         * Otherwise the app may start; [report] tells why it runs without the lock or the socket. For
         * "Started next to a copy that does not answer" its properties carry the last answer
         * ("answer": starting, quitting, frozen, silent, refused or none), every answer seen in order
         * ("answers", e.g. none>starting) and how long ago the other copy took the lock ("ownerAge").
         * A launch that quits for a copy still starting leaves a mark instead: see [takeJoinedLaunches].
         * [wait] is how long another copy gets to open its window or to let go of the lock.
         */
        fun acquire(
            root: File,
            wait: Duration = 20.seconds,
            answerTimeout: Duration = 2.seconds,
            report: (message: String, error: Throwable?, properties: Map<String, String>) -> Unit = { _, _, _ -> },
        ): SingleInstance? {
            val socket = File(root, "instance.sock")
            val lockFile = File(root, "instance.lock")
            var channel: FileChannel? = null
            var lastAnswer = NO_SOCKET
            // A copy seen quitting closes its socket a moment before it lets go of the lock: by then it
            // must not look like a copy that is still starting.
            var hasSeenQuitting = false
            val answersSeen = LinkedHashSet<String>()
            val started = TimeSource.Monotonic.markNow()
            try {
                root.mkdirs()
                val opened = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { channel = it }
                val deadline = TimeSource.Monotonic.markNow() + wait
                while (true) {
                    opened.tryLockOrNull()?.let { lock ->
                        markLockTime(opened, lockFile)
                        return owner(opened, lock, socket, report)
                    }
                    val answer = askFirstCopy(socket, answerTimeout)
                    if (answer == QUITTING) hasSeenQuitting = true
                    answersSeen += answerName(answer)
                    if (answer == OPEN) {
                        opened.close()
                        return null
                    }
                    if (!hasSeenQuitting && isYoung(lockFile)) {
                        opened.close()
                        leaveJoinedMark(root, JoinedLaunch(answerName(answer), waitBucket(started.elapsedNow())))
                        return null
                    }
                    lastAnswer = answer
                    // Starting, frozen, quitting or deaf: ask again, until it is time to start anyway.
                    if (deadline.hasPassedNow()) break
                    Thread.sleep(RETRY.inWholeMilliseconds)
                }
                report(
                    "Started next to a copy that does not answer",
                    null,
                    mapOf(
                        "answer" to answerName(lastAnswer),
                        "answers" to answersSeen.joinToString(">"),
                        "ownerAge" to ownerAgeBucket(lockFile),
                    ),
                )
            } catch (e: Exception) {
                // A folder on a network share, odd permissions, a full disk: run without the check.
                report("Cannot lock the data folder", e, emptyMap())
            }
            runCatching { channel?.close() }
            return SingleInstance(channel = null, lock = null, server = null, socket = socket)
        }

        private fun owner(
            channel: FileChannel,
            lock: FileLock,
            socket: File,
            report: (String, Throwable?, Map<String, String>) -> Unit,
        ): SingleInstance {
            // Marks of launches that joined an earlier copy, which quit before reading them.
            File(socket.parentFile, JOINED_DIR).listFiles()?.forEach { it.delete() }
            val server = try {
                // A socket file left by a crash would make the bind fail. Only the lock holder deletes it.
                Files.deleteIfExists(socket.toPath())
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
            } catch (e: Exception) {
                // The app runs; a second launch then waits for the lock and starts next to it.
                report("Cannot listen for a second launch", e, emptyMap())
                null
            }
            return SingleInstance(channel, lock, server, socket)
        }

        /**
         * The lock file's date says when its holder took the lock: see [isYoung]. Written through the
         * lock's own channel: on macOS, opening and closing another descriptor of this file (as
         * Files.setLastModifiedTime does) would drop the lock, a POSIX record lock. On Windows the lock
         * belongs to the handle, and the date is also set outright: NTFS may update a write's date late.
         */
        private fun markLockTime(channel: FileChannel, lockFile: File) {
            try {
                channel.write(ByteBuffer.wrap(byteArrayOf(1)), 0)
                if (IS_WINDOWS) Files.setLastModifiedTime(lockFile.toPath(), FileTime.from(Instant.now()))
            } catch (_: IOException) {
                // Then launches see an older date and treat this copy as they did before: they wait.
            }
        }

        /** The holder took the lock less than [YOUNG_OWNER] ago. A date in the future is not trusted. */
        private fun isYoung(lockFile: File): Boolean =
            lockAge(lockFile)?.let { it.toMillis() < YOUNG_OWNER.inWholeMilliseconds } ?: false

        /** How long ago the lock was taken, from the lock file's date. Null if unknown or in the future. */
        private fun lockAge(lockFile: File): java.time.Duration? = try {
            java.time.Duration.between(Files.getLastModifiedTime(lockFile.toPath()).toInstant(), Instant.now())
                .takeUnless { it.isNegative }
        } catch (_: IOException) {
            null
        }

        private fun ownerAgeBucket(lockFile: File): String {
            val minutes = lockAge(lockFile)?.toMinutes() ?: return "unknown"
            return when {
                minutes < 1 -> "under1m"
                minutes < 5 -> "1-5m"
                minutes < 60 -> "5-60m"
                else -> "over1h"
            }
        }

        /** One empty file per launch: creating a file is atomic, so launches at the same instant never clash. */
        private fun leaveJoinedMark(root: File, launch: JoinedLaunch) {
            try {
                val dir = File(root, JOINED_DIR).apply { mkdirs() }
                File(dir, launch.fileName()).createNewFile()
            } catch (_: IOException) {
                // The launch still quits: only the count in the first copy's log misses it.
            }
        }

        private fun waitBucket(waited: Duration): String = when {
            waited < 1.seconds -> "under1s"
            waited < 5.seconds -> "1-5s"
            waited < 20.seconds -> "5-20s"
            else -> "over20s"
        }

        // In the order of [waitBucket]: the longest wait is the one reported.
        private val WAIT_BUCKETS = listOf("under1s", "1-5s", "5-20s", "over20s")

        private fun countBucket(count: Int): String = when {
            count <= 2 -> count.toString()
            count <= 5 -> "3-5"
            else -> "over5"
        }

        // The lock is per process: in the same JVM (tests) a held lock throws instead of returning null.
        private fun FileChannel.tryLockOrNull(): FileLock? = try {
            tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }

        private fun answerName(answer: Byte) = when (answer) {
            STARTING -> "starting"
            QUITTING -> "quitting"
            FROZEN -> "frozen"
            SILENT -> "silent"
            REFUSED -> "refused"
            OPEN -> "open"
            else -> "none"
        }

        /**
         * The first copy's answer: [NO_SOCKET] if there is no socket file, [REFUSED] if there is one but
         * nobody accepts on it, [SILENT] if it does not reply in time.
         */
        private fun askFirstCopy(socket: File, timeout: Duration): Byte {
            // No socket yet (the first copy binds right after it locks) or none at all: ask again later.
            if (!socket.exists()) return NO_SOCKET
            // A file left by a copy that died, or a socket closed a moment ago by a copy that is quitting.
            val client = runCatching { SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())) }.getOrNull()
                ?: return REFUSED
            return client.use {
                runCatching {
                    // The first copy may be frozen: never wait for it without a limit.
                    it.configureBlocking(false)
                    Selector.open().use { selector ->
                        it.register(selector, SelectionKey.OP_READ)
                        // select(0) would wait forever.
                        if (selector.select(timeout.inWholeMilliseconds.coerceAtLeast(1)) == 0) return SILENT
                        val answer = ByteBuffer.allocate(1)
                        if (it.read(answer) == 1) answer.get(0) else SILENT
                    }
                }.getOrDefault(SILENT)
            }
        }
    }
}

/** A launch that quit counting on a copy still starting, as its mark's file name stores it. */
internal data class JoinedLaunch(val answer: String, val waited: String) {
    fun fileName(): String = "${answer}_${waited}_${ProcessHandle.current().pid()}_${System.nanoTime()}"

    companion object {
        fun parse(name: String): JoinedLaunch? {
            val parts = name.split("_")
            return if (parts.size == 4) JoinedLaunch(parts[0], parts[1]) else null
        }
    }
}
