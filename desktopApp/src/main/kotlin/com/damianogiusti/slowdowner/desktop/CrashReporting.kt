package com.damianogiusti.slowdowner.desktop

import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.Message
import java.io.File

/**
 * Crash reports to Sentry. Only release builds carry a DSN (-Dslowdowner.sentryDsn, set by the
 * release workflow): without it nothing is sent and every call here does nothing.
 * Reports carry no personal data: no IP, no computer name, and the home folder (which holds the
 * user's name) becomes `~`. The only link sent is the YouTube URL of a failed import.
 */
object CrashReporting {
    private const val DSN_PROPERTY = "slowdowner.sentryDsn"

    private var nativeCrashes: NativeCrashes? = null

    fun start(version: String, directories: AppDirectories) {
        // Never fail the start for crash reporting: the app works without it. A broken logs folder
        // only loses the native crash report, not the rest.
        val previous = runCatching {
            val crashes = NativeCrashes(directories.logs).also { nativeCrashes = it }
            crashes.collectPrevious().also { crashes.markRunning(version) }
        }.onFailure { System.err.println("Cannot check the previous run: $it") }.getOrNull()
        previous?.let { System.err.println("The previous run crashed in native code: see native-crash.log") }

        val dsn = System.getProperty(DSN_PROPERTY)?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            Sentry.init { options ->
                options.dsn = dsn
                options.release = release(version)
                options.environment = "production"
                // Events written while offline are sent at the next start.
                options.cacheDirPath = File(directories.cache, "sentry").path
                // The computer's name is often the owner's name ("Anna's MacBook").
                options.isAttachServerName = false
                // The native crash event would get the stack of the thread that sends it: misleading.
                options.isAttachStacktrace = false
                options.setTag("os", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
                options.setTag("arch", System.getProperty("os.arch"))
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
            }
            Sentry.startSession()
            previous?.let(::send)
        }.onFailure { System.err.println("Cannot start crash reporting: $it") }
    }

    /** A clean exit: the next start finds no marker and reports nothing. */
    fun stop() {
        runCatching {
            nativeCrashes?.markStopped()
            Sentry.endSession()
            Sentry.close()
        }
    }

    fun reportImportFailure(url: String?, error: Throwable) {
        Sentry.captureException(error) { scope ->
            scope.setTag("feature", "import")
            url?.let { scope.setExtra("url", publicLink(it)) }
        }
    }

    private fun send(crash: NativeCrash) {
        val excerpt = crash.excerpt
        val frame = excerpt.problematicFrame ?: "unknown frame"
        val event = SentryEvent().apply {
            level = SentryLevel.FATAL
            message = Message().apply { formatted = "Native crash: ${excerpt.signal ?: "unknown signal"} in $frame" }
            crash.version?.let { release = release(it) }
            setTag("crash", "native")
            // Offsets change with every build: group by library and function only.
            fingerprints = listOf("native-crash", frame.replace(OFFSET, ""))
        }
        val attachment = Attachment(scrubHome(excerpt.text).orEmpty().toByteArray(), "hs_err-excerpt.txt", "text/plain")
        Sentry.captureEvent(event, Hint.withAttachment(attachment))
    }

    private fun release(version: String) = "downstep@$version"

    private val home: String? = System.getProperty("user.home")

    private fun scrub(event: SentryEvent): SentryEvent = event.apply {
        exceptions?.forEach { it.value = scrubHome(it.value, home) }
        message?.let {
            it.formatted = scrubHome(it.formatted, home)
            it.message = scrubHome(it.message, home)
        }
    }

    private val OFFSET = Regex("""\+0x[0-9a-fA-F]+""")
}

/** Also the forms a Windows home takes in URLs and Java paths: `C:/Users/anna`, `file:///c:/Users/anna`. */
internal fun scrubHome(text: String?, home: String? = System.getProperty("user.home")): String? {
    if (text == null || home.isNullOrBlank()) return text
    return listOf(home, home.replace('\\', '/')).distinct().fold(text) { scrubbed, form ->
        scrubbed.replace(form, "~", ignoreCase = true)
    }
}

/**
 * A pasted link can carry more than the video: a playlist (`list=`), a share token (`si=`). Only
 * the video id is kept.
 */
internal fun publicLink(url: String): String {
    val query = url.substringAfter('?', "").substringBefore('#')
    val video = query.split('&').firstOrNull { it.startsWith("v=") }
    return url.substringBefore('?').substringBefore('#') + (video?.let { "?$it" } ?: "")
}
