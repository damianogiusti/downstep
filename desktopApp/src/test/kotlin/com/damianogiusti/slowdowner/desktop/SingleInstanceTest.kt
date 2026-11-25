package com.damianogiusti.slowdowner.desktop

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileTime
import java.time.Instant
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
    private val reports = mutableListOf<Map<String, String>>()

    // The last answer of each "Started next to a copy that does not answer".
    private val answers get() = reports.map { mapOf("answer" to it.getValue("answer")) }

    @AfterTest
    fun closeAll() = toClose.forEach { runCatching { it.close() } }

    private fun acquire(dir: File = root, wait: Duration = 10.seconds): SingleInstance? =
        SingleInstance.acquire(dir, wait, answerTimeout = 500.milliseconds) { message, _, properties ->
            synchronized(problems) { problems += message; reports += properties }
        }?.also { toClose += it }

    private val lockFile by lazy { File(root, "instance.lock") }

    /** The lock was taken long ago: launches no longer count on that copy opening its window. */
    private fun ageLock(by: java.time.Duration = java.time.Duration.ofMinutes(2)) {
        Files.setLastModifiedTime(lockFile.toPath(), FileTime.from(Instant.now().minus(by)))
    }

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
    fun `when a launch comes while an old copy is still starting then it quits once the window is open`() {
        val first = assertNotNull(acquire())
        ageLock()
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
        ageLock()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf("Started next to a copy that does not answer"), problems)
    }

    @Test
    fun `when an old copy has no window yet then the launch reports starting`() {
        assertNotNull(acquire())
        ageLock()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "starting")), answers)
    }

    @Test
    fun `when the first copy is quitting then the launch reports quitting`() {
        assertNotNull(acquire()).quitting()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "quitting")), answers)
    }

    @Test
    fun `when the first window does not come forward then the launch reports frozen`() {
        assertNotNull(acquire()).onAnotherLaunch = { false }
        ageLock()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "frozen")), answers)
    }

    @Test
    fun `when nobody listens then the launch reports none`() {
        val channel = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()
        ageLock()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "none")), answers)
    }

    @Test
    fun `when the first copy fails to come forward then it still answers the next launch`() {
        val first = assertNotNull(acquire())
        first.onAnotherLaunch = { error("broken") }
        ageLock()
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
        val channel = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()
        ageLock()
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            .bind(UnixDomainSocketAddress.of(File(root, "instance.sock").toPath()))
            .also { toClose += it }

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf("Started next to a copy that does not answer"), problems)
        assertEquals(listOf(mapOf("answer" to "silent")), answers)
    }

    @Test
    fun `when the first copy took the lock moments ago and has no window yet then a launch quits at once`() {
        val first = assertNotNull(acquire())

        val started = System.nanoTime()
        assertNull(acquire(wait = 10.seconds))

        assertTrue(System.nanoTime() - started < 5_000_000_000L, "it must not wait for the window")
        assertTrue(problems.isEmpty())
        // Once its window exists, the first copy comes forward for that launch, and logs it once.
        assertEquals(
            mapOf("while" to "starting", "launches" to "1", "answers" to "starting", "waited" to "under1s"),
            first.takeJoinedLaunches(),
        )
        assertNull(first.takeJoinedLaunches())
    }

    @Test
    fun `when another process starts while the first copy is starting then it quits at once`() {
        val first = assertNotNull(acquire())

        val java = File(System.getProperty("java.home"), "bin/java").path
        val second = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SecondLaunch::class.java.name, root.path)
            .inheritIO()
            .start()

        // Well under the 20 s wait: it did not wait for a window.
        assertTrue(second.waitFor(15, TimeUnit.SECONDS))
        assertEquals(SecondLaunch.QUIT, second.exitValue())
        assertEquals("1", first.takeJoinedLaunches()?.get("launches"))
    }

    @Test
    fun `when the copy holding the lock took it moments ago but does not answer then a launch quits`() {
        // Locked, no socket yet: answer none. Locked by this test just now, so the date is recent.
        val channel = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()

        assertNull(acquire(wait = 10.seconds))

        assertTrue(problems.isEmpty())
    }

    @Test
    fun `when a young copy is quitting then the launch does not count on it`() {
        assertNotNull(acquire()).quitting()

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "quitting")), answers)
    }

    @Test
    fun `when the lock file is dated in the future then the copy is not taken for young`() {
        assertNotNull(acquire())
        ageLock(by = java.time.Duration.ofMinutes(-10))

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "starting")), answers)
    }

    @Test
    fun `when a copy takes the lock then it dates the lock file`() {
        lockFile.writeText("")
        ageLock(by = java.time.Duration.ofHours(1))

        assertNotNull(acquire())

        val age = java.time.Duration.between(Files.getLastModifiedTime(lockFile.toPath()).toInstant(), Instant.now())
        assertTrue(age < java.time.Duration.ofMinutes(1), "lock file dated $age ago")
    }

    @Test
    fun `when a copy opens its window with no launch waiting then nothing is reported`() {
        assertNull(assertNotNull(acquire()).takeJoinedLaunches())
    }

    @Test
    fun `when a copy takes the lock then it drops marks left for an earlier copy`() {
        // Locked, no socket: the launches see none.
        val channel = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()
        repeat(3) { assertNull(acquire()) }
        channel.close()
        val first = assertNotNull(acquire())

        // The owner clears marks left for an earlier copy: these three came before it took the lock.
        assertNull(first.takeJoinedLaunches())
    }

    @Test
    fun `when launches join the starting copy then their answers are counted`() {
        val first = assertNotNull(acquire())
        repeat(3) { assertNull(acquire()) }

        val joined = assertNotNull(first.takeJoinedLaunches())

        assertEquals("3-5", joined["launches"])
        assertEquals("starting", joined["answers"])
    }

    @Test
    fun `when an old copy never answers then the report has every answer and the lock age`() {
        assertNotNull(acquire())
        ageLock(by = java.time.Duration.ofMinutes(10))

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(mapOf("answer" to "starting", "answers" to "starting", "ownerAge" to "5-60m"), reports.single())
    }

    @Test
    fun `when a socket file is there but nobody accepts on it then the launch reports refused`() {
        val channel = FileChannel.open(lockFile.toPath(), CREATE, WRITE).also { toClose += it }
        channel.lock()
        ageLock()
        File(root, "instance.sock").writeText("left by a copy that died")

        assertNotNull(acquire(wait = 300.milliseconds))

        assertEquals(listOf(mapOf("answer" to "refused")), answers)
    }

    @Test
    fun `a joined launch survives its mark's file name`() {
        val launch = JoinedLaunch("none", "1-5s")

        assertEquals(launch, JoinedLaunch.parse(launch.fileName()))
        assertNull(JoinedLaunch.parse("something else"))
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
