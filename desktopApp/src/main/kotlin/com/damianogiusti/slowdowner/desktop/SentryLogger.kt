package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.Logger
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.Message

/**
 * Release builds: info and above go to Sentry Logs. An error with a throwable also becomes an issue,
 * with the full stack trace, and its log line carries the issue id. A warning has no issue, so its
 * log line carries a short stack trace. Debug lines are dropped. CrashReporting scrubs paths from
 * every text before sending.
 */
class SentryLogger : Logger {

    override fun d(message: String, properties: Map<String, String>) = Unit

    override fun i(message: String, properties: Map<String, String>) =
        log(SentryLogLevel.INFO, message, properties)

    override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) =
        log(SentryLogLevel.WARN, message, properties + throwable?.let(::describe).orEmpty())

    override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) {
        if (throwable == null) return log(SentryLogLevel.ERROR, message, properties)
        val event = SentryEvent(throwable).apply {
            level = SentryLevel.ERROR
            this.message = Message().apply { formatted = message }
            properties.forEach { (key, value) -> setExtra(key, value) }
        }
        val issue = Sentry.captureEvent(event)
        log(SentryLogLevel.ERROR, message, properties + mapOf("error" to summary(throwable), "issue" to issue.toString()))
    }

    private fun log(level: SentryLogLevel, message: String, properties: Map<String, String>) {
        val attributes = properties.map { (key, value) -> SentryAttribute.stringAttribute(key, value) }
        Sentry.logger().log(level, SentryLogParameters.create(SentryAttributes.of(*attributes.toTypedArray())), message)
    }

    private fun describe(throwable: Throwable) =
        mapOf("error" to summary(throwable), "stacktrace" to shortStackTrace(throwable))

    private fun summary(throwable: Throwable) = "${throwable.javaClass.name}: ${throwable.message}"

    /** Enough to find the line: the cause chain, each with its top frames. */
    private fun shortStackTrace(throwable: Throwable): String =
        generateSequence(throwable) { it.cause }.take(MAX_CAUSES).joinToString("\n") { cause ->
            val frames = cause.stackTrace.take(MAX_FRAMES).joinToString("\n") { "    at $it" }
            val more = cause.stackTrace.size - MAX_FRAMES
            buildString {
                if (cause !== throwable) append("Caused by: ")
                append(summary(cause)).append('\n').append(frames)
                if (more > 0) append("\n    ... $more more")
            }
        }

    private companion object {
        const val MAX_CAUSES = 3
        const val MAX_FRAMES = 12
    }
}
