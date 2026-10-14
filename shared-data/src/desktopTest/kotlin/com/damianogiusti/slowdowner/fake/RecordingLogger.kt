package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.logging.Logger

/** Keeps every line, so a test can check what a failure logs and at which level. */
class RecordingLogger : Logger {

    data class Line(val level: Char, val message: String, val throwable: Throwable?, val properties: Map<String, String>)

    val lines = mutableListOf<Line>()

    fun at(level: Char) = lines.filter { it.level == level }

    override fun d(message: String, properties: Map<String, String>) {
        lines += Line('D', message, null, properties)
    }

    override fun i(message: String, properties: Map<String, String>) {
        lines += Line('I', message, null, properties)
    }

    override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) {
        lines += Line('W', message, throwable, properties)
    }

    override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) {
        lines += Line('E', message, throwable, properties)
    }
}
