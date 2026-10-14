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
 * Release builds: info and above go to Sentry Logs, and an error with a throwable also becomes an
 * issue, with its stack trace. Debug lines are dropped. CrashReporting scrubs both before sending.
 */
class SentryLogger : Logger {

    override fun d(message: String, properties: Map<String, String>) = Unit

    override fun i(message: String, properties: Map<String, String>) =
        log(SentryLogLevel.INFO, message, null, properties)

    override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) =
        log(SentryLogLevel.WARN, message, throwable, properties)

    override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) {
        log(SentryLogLevel.ERROR, message, throwable, properties)
        if (throwable == null) return
        val event = SentryEvent(throwable).apply {
            level = SentryLevel.ERROR
            this.message = Message().apply { formatted = message }
            properties.forEach { (key, value) -> setExtra(key, value) }
        }
        Sentry.captureEvent(event)
    }

    private fun log(level: SentryLogLevel, message: String, throwable: Throwable?, properties: Map<String, String>) {
        // Only the type: an exception message often names a file. The issue of an error keeps the
        // message, scrubbed by CrashReporting.
        val cause = throwable?.let { "error" to it.javaClass.name }
        val attributes = (properties + listOfNotNull(cause))
            .map { (key, value) -> SentryAttribute.stringAttribute(key, value) }
        Sentry.logger().log(level, SentryLogParameters.create(SentryAttributes.of(*attributes.toTypedArray())), message)
    }
}
