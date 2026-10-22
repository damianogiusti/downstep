package com.damianogiusti.slowdowner.desktop

import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.concurrent.thread

/**
 * One copy of the app per data folder: two copies would share the database and the audio device, and
 * quitting one freed the engine under the other's feet. On Windows people start the app again while
 * the first copy is still opening. The later copy asks the first one to come forward, then quits.
 *
 * The lock is the operating system's: it goes away with the process, even after a crash. The call
 * goes through a Unix domain socket next to it, which, unlike a TCP port, raises no firewall prompt.
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocketChannel?,
    private val socket: File,
) : AutoCloseable {

    /** Called on the listener thread when another copy starts. Null until the window exists. */
    @Volatile
    var onAnotherLaunch: (() -> Unit)? = null

    init {
        server?.let { listening ->
            thread(isDaemon = true, name = "single-instance") { accept(listening) }
        }
    }

    private fun accept(listening: ServerSocketChannel) {
        while (true) {
            // Fails once close() closes the socket: nothing else to do.
            val client = runCatching { listening.accept() }.getOrNull() ?: break
            client.close()
            onAnotherLaunch?.invoke()
        }
    }

    override fun close() {
        runCatching { server?.close() }
        socket.delete()
        runCatching { lock.release() }
        channel.close()
    }

    companion object {
        private const val CONNECT_ATTEMPTS = 10
        private const val CONNECT_RETRY_MS = 200L

        /**
         * The lock on [root], or null if another copy holds it: that copy has been told already.
         * [onListenFailed] reports a socket that cannot be opened: the app still runs, but a second
         * launch then quits without bringing this one forward.
         */
        fun acquire(root: File, onListenFailed: (IOException) -> Unit = {}): SingleInstance? {
            root.mkdirs()
            val socket = File(root, "instance.sock")
            val channel = FileChannel.open(File(root, "instance.lock").toPath(), CREATE, WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                // The lock is per process: a second acquire in the same JVM (tests) lands here.
                null
            }
            if (lock == null) {
                channel.close()
                callFirstCopy(socket)
                return null
            }
            val server = try {
                // A socket file left by a crash would make the bind fail. Only the lock holder deletes it.
                Files.deleteIfExists(socket.toPath())
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket.toPath()))
            } catch (e: IOException) {
                onListenFailed(e)
                null
            }
            return SingleInstance(channel, lock, server, socket)
        }

        // The first copy binds right after it locks: a launch in between waits a moment for it.
        private fun callFirstCopy(socket: File) {
            repeat(CONNECT_ATTEMPTS) {
                try {
                    SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).close()
                    return
                } catch (_: IOException) {
                    Thread.sleep(CONNECT_RETRY_MS)
                }
            }
        }
    }
}
