package com.damianogiusti.slowdowner.desktop

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Mostly two copies in one JVM, where the lock fails differently: one test starts a real second
// process. CI also starts the packed exe twice (windows.yml, "Start the packed app").
class SingleInstanceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val root by lazy { folder.newFolder("data") }
    private val acquired = mutableListOf<SingleInstance>()

    @AfterTest
    fun closeAll() = acquired.forEach { it.close() }

    private fun acquire(): SingleInstance? = SingleInstance.acquire(root) { throw it }?.also { acquired += it }

    @Test
    fun `when no copy runs then the lock is taken`() {
        assertNotNull(acquire())
    }

    @Test
    fun `when a copy runs then a second launch does not get the lock`() {
        assertNotNull(acquire())

        assertNull(acquire())
    }

    @Test
    fun `when a second launch happens then the first copy is told`() {
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown() }

        acquire()

        assertTrue(told.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `when another process starts then it quits and the first copy is told`() {
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown() }

        val java = File(System.getProperty("java.home"), "bin/java").path
        val second = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), SecondLaunch::class.java.name, root.path)
            .inheritIO()
            .start()

        assertTrue(second.waitFor(30, TimeUnit.SECONDS))
        assertEquals(SecondLaunch.DID_NOT_GET_THE_LOCK, second.exitValue())
        assertTrue(told.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `when the first copy quits then the next launch gets the lock`() {
        assertNotNull(acquire()).close()

        assertNotNull(acquire())
    }

    @Test
    fun `a socket file left by a crash does not stop the next copy`() {
        File(root, "instance.sock").writeText("left by a crash")
        val told = CountDownLatch(1)
        assertNotNull(acquire()).onAnotherLaunch = { told.countDown() }

        acquire()

        assertTrue(told.await(5, TimeUnit.SECONDS))
    }
}

/** The second process of the test above. */
object SecondLaunch {
    const val DID_NOT_GET_THE_LOCK = 0
    private const val GOT_THE_LOCK = 3

    @JvmStatic
    fun main(args: Array<String>) {
        val instance = SingleInstance.acquire(File(args[0]))
        exitProcess(if (instance == null) DID_NOT_GET_THE_LOCK else GOT_THE_LOCK)
    }
}
