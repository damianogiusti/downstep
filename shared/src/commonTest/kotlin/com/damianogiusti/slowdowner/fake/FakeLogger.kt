package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.logging.Logger

/** Records info lines, the ones that reach the release logs as usage events. */
class FakeLogger : Logger {

    data class Line(val message: String, val properties: Map<String, String>)

    val infos = mutableListOf<Line>()

    override fun d(message: String, properties: Map<String, String>) = Unit

    override fun i(message: String, properties: Map<String, String>) {
        infos += Line(message, properties)
    }

    override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit

    override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) = Unit
}
