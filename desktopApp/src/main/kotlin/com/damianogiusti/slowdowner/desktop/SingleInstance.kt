package com.damianogiusti.slowdowner.desktop

import java.io.File
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
 * The rule: a launch gives up only when another copy answers that its window came forward. A copy
 * that is still starting, frozen, quitting, silent or that cannot be checked never keeps the app
 * closed: the launch keeps asking and trying the lock for a while, then starts anyway. At worst two
 * copies run, as before this check.
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
                val cameForward = !isQuitting && runCatching { onAnotherLaunch?.invoke() == true }.getOrDefault(false)
                runCatching { it.write(ByteBuffer.wrap(byteArrayOf(if (cameForward) OPEN else BUSY))) }
            }
        }
    }

    override fun close() {
        runCatching { server?.close() }
        if (server != null) socket.delete()
        runCatching { lock?.release() }
        runCatching { channel?.close() }
    }

    companion object {
        private const val OPEN: Byte = 1
        private const val BUSY: Byte = 2
        private val RETRY = 300.milliseconds
        private val ACCEPT_RETRY = 100.milliseconds

        /**
         * Null only when another copy answered that its window came forward: this launch should quit.
         * Otherwise the app may start; [report] tells why it runs without the lock or the socket.
         * [wait] is how long another copy gets to open its window or to let go of the lock.
         */
        fun acquire(
            root: File,
            wait: Duration = 20.seconds,
            answerTimeout: Duration = 2.seconds,
            report: (message: String, error: Throwable?) -> Unit = { _, _ -> },
        ): SingleInstance? {
            val socket = File(root, "instance.sock")
            var channel: FileChannel? = null
            try {
                root.mkdirs()
                val opened = FileChannel.open(File(root, "instance.lock").toPath(), CREATE, WRITE).also { channel = it }
                val deadline = TimeSource.Monotonic.markNow() + wait
                while (true) {
                    opened.tryLockOrNull()?.let { return owner(opened, it, socket, report) }
                    if (askFirstCopy(socket, answerTimeout) == OPEN) {
                        opened.close()
                        return null
                    }
                    // Starting, frozen, quitting or deaf: ask again, until it is time to start anyway.
                    if (deadline.hasPassedNow()) break
                    Thread.sleep(RETRY.inWholeMilliseconds)
                }
                report("Started next to a copy that does not answer", null)
            } catch (e: Exception) {
                // A folder on a network share, odd permissions, a full disk: run without the check.
                report("Cannot lock the data folder", e)
            }
            runCatching { channel?.close() }
            return SingleInstance(channel = null, lock = null, server = null, socket = socket)
        }

        private fun owner(
            channel: FileChannel,
            lock: FileLock,
            socket: File,
            report: (String, Throwable?) -> Unit,
        ): SingleInstance {
            val server = try {
                // A socket file left by a crash would make the bind fail. Only the lock holder deletes it.
                Files.deleteIfExists(socket.toPath())
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
            } catch (e: Exception) {
                // The app runs; a second launch then waits for the lock and starts next to it.
                report("Cannot listen for a second launch", e)
                null
            }
            return SingleInstance(channel, lock, server, socket)
        }

        // The lock is per process: in the same JVM (tests) a held lock throws instead of returning null.
        private fun FileChannel.tryLockOrNull(): FileLock? = try {
            tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }

        /** The first copy's answer, or null if it cannot be reached or stays silent. */
        private fun askFirstCopy(socket: File, timeout: Duration): Byte? {
            // No socket yet (the first copy binds right after it locks) or none at all: ask again later.
            val client = runCatching { SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())) }.getOrNull()
                ?: return null
            return client.use {
                runCatching {
                    // The first copy may be frozen: never wait for it without a limit.
                    it.configureBlocking(false)
                    Selector.open().use { selector ->
                        it.register(selector, SelectionKey.OP_READ)
                        // select(0) would wait forever.
                        if (selector.select(timeout.inWholeMilliseconds.coerceAtLeast(1)) == 0) return null
                        val answer = ByteBuffer.allocate(1)
                        if (it.read(answer) == 1) answer.get(0) else null
                    }
                }.getOrNull()
            }
        }
    }
}
