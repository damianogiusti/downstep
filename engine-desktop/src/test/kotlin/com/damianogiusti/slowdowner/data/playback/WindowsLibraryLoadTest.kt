package com.damianogiusti.slowdowner.data.playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The policy block itself cannot be staged on CI: these check the codes and the call that reads them. */
class WindowsLibraryLoadTest {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    @Test
    fun `application control and group policy codes are a policy block`() {
        assertTrue(WindowsLibraryLoad.isBlockedByPolicy(4551))
        assertTrue(WindowsLibraryLoad.isBlockedByPolicy(1260))
    }

    @Test
    fun `a broken or missing file is not a policy block`() {
        assertFalse(WindowsLibraryLoad.isBlockedByPolicy(193))
        assertFalse(WindowsLibraryLoad.isBlockedByPolicy(126))
        assertFalse(WindowsLibraryLoad.isBlockedByPolicy(null))
    }

    @Test
    fun `the error code of a file that is not a library is read from Windows`() {
        val notALibrary = File.createTempFile("sdengine", ".dll").apply { writeText("not a library") }
        try {
            val code = WindowsLibraryLoad.errorCode(notALibrary.toPath())

            if (isWindows) assertEquals(ERROR_BAD_EXE_FORMAT, code) else assertNull(code)
        } finally {
            notALibrary.delete()
        }
    }

    @Test
    fun `the error code of a missing file is read from Windows`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "sdengine-missing-${System.nanoTime()}.dll")

        val code = WindowsLibraryLoad.errorCode(missing.toPath())

        if (isWindows) assertEquals(ERROR_MOD_NOT_FOUND, code) else assertNull(code)
    }

    @Test
    fun `a load error that is not a policy block is kept as it is`() {
        val notALibrary = File.createTempFile("sdengine", ".dll").apply { writeText("not a library") }
        try {
            val error = UnsatisfiedLinkError("cannot load")

            assertSame(error, WindowsLibraryLoad.explain(notALibrary.toPath(), error))
        } finally {
            notALibrary.delete()
        }
    }

    private companion object {
        const val ERROR_MOD_NOT_FOUND = 126
        const val ERROR_BAD_EXE_FORMAT = 193
    }
}
