package com.damianogiusti.slowdowner.logging

/** Development builds: everything goes to the standard output, nothing is sent. */
class ConsoleLogger(private val print: (String) -> Unit = ::println) : Logger {

    override fun d(message: String, properties: Map<String, String>) = write("D", message, null, properties)

    override fun i(message: String, properties: Map<String, String>) = write("I", message, null, properties)

    override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) =
        write("W", message, throwable, properties)

    override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) =
        write("E", message, throwable, properties)

    private fun write(level: String, message: String, throwable: Throwable?, properties: Map<String, String>) {
        val line = buildString {
            append(level).append('/').append(message)
            if (properties.isNotEmpty()) append(' ').append(properties)
            throwable?.let { append('\n').append(it.stackTraceToString()) }
        }
        print(line)
    }
}
