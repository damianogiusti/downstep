package com.damianogiusti.slowdowner.desktop

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Mostly two copies in one JVM, where the lock fails differently: one test starts a real second
// process. CI also starts the packed exe twice (windows.yml, "Start the packed app").
class SingleInstanceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root by lazy { folder.newFolder("data") }
    private val toClose = mutableListOf<AutoCloseable>()
    private val problems = mutableListOf<String>()

    @AfterTest
    fun closeAll() = toClose.forEach { runCatching { it.close() } }

    private fun acquire(dir: File = root, wait: Duration = 10.seconds): SingleInstance? =
        SingleInstance.acquire(dir, wait, answerTimeout = 500.milliseconds) { message, _ ->
            synchronized(problems) { problems += message }
        }?.also { toClose += it }

    @Test
    fun `when no copy runs then the lock is taken`() {
        assertNotNull(acquire())
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `when a copy is open then a second launch quits`() {
        assertNotNull(acquire()).onAnotherLaunch = { true }

        assertNull(acquire())
    }

    @Test
    fun `when a launch comes while the first copy is starting then it quits once the window is open`() {
        val first = assertNotNull(acquire())
        var second: SingleInstance? = null
        val launch = thread { second = acquire() }

        // The window opens a moment later.
        Thread.sleep(500)
        first.onAnotherLaunch = { true }
        launch.join(10_000)

        assertFalse(launch.isAlive)
        assertNull(second)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `when the first window is frozen then the launch starts anyway`() {
        assertNotNull(acquire()).onAnotherLaunch = { false }

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf("Started next to a copy that does not answer"), problems)
    }

    @Test
    fun `when the first copy fails to come forward then it still answers the next launch`() {
        val first = assertNotNull(acquire())
        first.onAnotherLaunch = { error("broken") }
        assertNotNull(acquire(wait = 300.milliseconds))
        first.onAnotherLaunch = { true }

        assertNull(acquire())
    }

    @Test
    fun `when a second launch happens then the first copy is told`() {
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown(); true }

        acquire()

        assertTrue(told.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `when another process starts then it quits and the first copy is told`() {
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown(); true }

        val java = File(System.getProperty("java.home"), "bin/java").path
        val second = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SecondLaunch::class.java.name, root.path)
            .inheritIO()
            .start()

        assertTrue(second.waitFor(30, TimeUnit.SECONDS))
        assertEquals(SecondLaunch.QUIT, second.exitValue())
        assertTrue(told.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `when the first copy quits then the next launch gets the lock`() {
        assertNotNull(acquire()).close()

        assertNotNull(acquire()).onAnotherLaunch = { true }
        assertNull(acquire())
    }

    @Test
    fun `when a socket file is left by a crash then the next copy starts`() {
        File(root, "instance.sock").writeText("left by a crash")
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown(); true }

        acquire()

        assertTrue(told.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `when a launch comes while the first copy is quitting then it waits and takes over`() {
        val first = assertNotNull(acquire())
        first.quitting()
        var second: SingleInstance? = null
        val launch = thread { second = acquire() }

        // Long enough for the launch to be waiting: if it comes later it takes the free lock, and passes too.
        Thread.sleep(300)
        first.close()
        launch.join(10_000)

        val owner = assertNotNull(second)
        assertTrue(problems.isEmpty())
        // It holds the lock for real: the next launch finds it open.
        owner.onAnotherLaunch = { true }
        assertNull(acquire())
    }

    @Test
    fun `when the first copy is quitting and never exits then the launch starts anyway`() {
        val told = CountDownLatch(1)
        val first = assertNotNull(acquire())
        first.onAnotherLaunch = { told.countDown(); true }
        first.quitting()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf("Started next to a copy that does not answer"), problems)
        assertFalse(told.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `when the copy holding the lock never answers then the launch starts anyway`() {
        // A frozen copy: the lock is held and the socket accepts, but nobody replies.
        val channel = FileChannel.open(File(root, "instance.lock").toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            .bind(UnixDomainSocketAddress.of(File(root, "instance.sock").toPath()))
            .also { toClose += it }

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf("Started next to a copy that does not answer"), problems)
    }

    @Test
    fun `when the data folder cannot be locked then the app starts anyway`() {
        val notAFolder = folder.newFile("data-is-a-file")

        assertNotNull(acquire(dir = notAFolder))

        assertEquals(listOf("Cannot lock the data folder"), problems)
    }
}

/** The second process of the test above. */
object SecondLaunch {
    const val QUIT = 0
    private const val STARTED = 3

    @JvmStatic
    fun main(args: Array<String>) {
        val instance = SingleInstance.acquire(File(args[0]))
        exitProcess(if (instance == null) QUIT else STARTED)
    }
}
