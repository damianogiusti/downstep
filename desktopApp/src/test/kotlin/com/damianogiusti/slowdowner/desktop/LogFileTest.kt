package com.damianogiusti.slowdowner.desktop

import java.io.File
import java.io.PrintStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogFileTest {

    private val file = File.createTempFile("slowdowner", ".log")
    private val log = LogFile(file, maxBytes = 64)

    @AfterTest
    fun tearDown() {
        log.close()
        file.delete()
    }

    @Test
    fun `stdout lines wait in the buffer, an stderr line takes them along in order`() {
        val out = PrintStream(log.flushedLater(), true, Charsets.UTF_8)
        val err = PrintStream(log, true, Charsets.UTF_8)

        out.println("one")
        out.println("two")
        assertEquals("", file.readText())

        err.println("boom")
        assertEquals("one\ntwo\nboom\n", file.readText().replace("\r\n", "\n"))
    }

    @Test
    fun `the file stops growing at the cap`() {
        val out = PrintStream(log.flushedLater(), true, Charsets.UTF_8)

        repeat(20) { out.println("0123456789") }
        log.flush()

        assertEquals(64, file.length())
    }
}
