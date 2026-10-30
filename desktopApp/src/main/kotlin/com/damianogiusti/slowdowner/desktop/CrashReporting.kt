package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import com.damianogiusti.slowdowner.logging.Logger
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryAttributeType
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryLogEvent
import io.sentry.SentryLogEventAttributeValue
import io.sentry.SentryOptions
import io.sentry.protocol.Message
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.User
import java.io.File
import java.util.UUID

/**
 * Crash reports and logs to Sentry. Only release builds carry a DSN (-Dslowdowner.sentryDsn, set by
 * the release workflow): without it nothing is sent and every call here does nothing.
 * Reports carry no personal data: no IP, no computer name, and the home folder (which holds the
 * user's name) becomes `~`. The only link sent is the YouTube URL of a failed import. Each
 * installation has a random id, so crashes can be counted per user; deleting the data folder resets it.
 */
object CrashReporting {
    private const val DSN_PROPERTY = "slowdowner.sentryDsn"

    private var nativeCrashes: NativeCrashes? = null

    /** True if this run created the installation id: a first start, or a data folder deleted since. Null without a DSN. */
    var isNewInstallation: Boolean? = null
        private set

    fun start(version: String, directories: AppDirectories) {
        // Never fail the start for crash reporting: the app works without it. A broken logs folder
        // only loses the native crash report, not the rest.
        val previous = runCatching {
            val crashes = NativeCrashes(directories.logs).also { nativeCrashes = it }
            crashes.collectPrevious().also { crashes.markRunning(version) }
        }.onFailure { System.err.println("Cannot check the previous run: $it") }.getOrNull()
        previous?.let { System.err.println("The previous run crashed in native code: see native-crash.log") }

        isNewInstallation = null
        val dsn = System.getProperty(DSN_PROPERTY)?.takeIf { it.isNotBlank() } ?: return
        // Without the id, users are not counted but crashes still arrive.
        val installation = runCatching { installationId(directories) }
            .onFailure { System.err.println("Cannot keep the installation id: $it") }
            .getOrNull()
        runCatching {
            Sentry.init { options ->
                options.dsn = dsn
                options.release = release(version)
                options.environment = System.getProperty("slowdowner.sentryEnvironment") ?: "production"
                // Events written while offline are sent at the next start.
                options.cacheDirPath = File(directories.cache, "sentry").path
                // The computer's name is often the owner's name ("Anna's MacBook").
                options.isAttachServerName = false
                // The native crash event would get the stack of the thread that sends it: misleading.
                options.isAttachStacktrace = false
                options.setTag("os", osDescription)
                options.setTag("arch", System.getProperty("os.arch"))
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
                options.logs.isEnabled = true
                options.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { log -> scrub(log) }
                // Sessions count users by this id, events show it as the user.
                options.distinctId = installation
            }
            installation?.let { id -> Sentry.setUser(User().apply { this.id = id }) }
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

    /** Sends what is queued, waiting at most [timeout]: used when the app is about to be halted. */
    fun flush(timeout: kotlin.time.Duration) {
        runCatching { Sentry.flush(timeout.inWholeMilliseconds) }
    }

    /** Development prints everything and sends nothing; a release sends and prints nothing. */
    fun logger(): Logger = if (Sentry.isEnabled()) SentryLogger() else ConsoleLogger()

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

    /** Random, not derived from the computer or the person. */
    private fun installationId(directories: AppDirectories): String {
        val file = File(directories.root, "installation-id")
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            isNewInstallation = false
            return it
        }
        isNewInstallation = true
        return UUID.randomUUID().toString().also { id ->
            directories.root.mkdirs()
            file.writeText(id)
        }
    }

    private val home: String? = System.getProperty("user.home")

    private fun scrub(event: SentryEvent): SentryEvent = event.apply {
        // The JVM SDK sends no OS context: without it Sentry cannot tell macOS from Windows.
        if (contexts.operatingSystem == null) {
            contexts.setOperatingSystem(
                OperatingSystem().apply {
                    name = osName
                    version = osVersion
                    rawDescription = osDescription
                },
            )
        }
        exceptions?.forEach { it.value = scrubText(it.value) }
        message?.let {
            it.formatted = scrubText(it.formatted)
            it.message = scrubText(it.message)
        }
        extras?.forEach { (key, value) -> if (value is String) setExtra(key, scrubText(value)) }
    }

    private fun scrub(log: SentryLogEvent): SentryLogEvent = log.apply {
        body = scrubText(body).orEmpty()
        attributes = attributes?.mapValues { (_, attribute) ->
            val value = attribute.value
            if (value is String) SentryLogEventAttributeValue(attribute.type, scrubText(value)) else attribute
        }.orEmpty() + mapOf(
            // Tags in options reach only events: logs need the OS as attributes to be split by platform.
            "os.name" to SentryLogEventAttributeValue(SentryAttributeType.STRING, osName),
            "os.version" to SentryLogEventAttributeValue(SentryAttributeType.STRING, osVersion),
        )
    }

    private fun scrubText(text: String?) = scrubPaths(scrubHome(text, home))

    private val osDescription = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
    private val osName = osFamily(System.getProperty("os.name"))
    private val osVersion = System.getProperty("os.version").orEmpty()

    private val OFFSET = Regex("""\+0x[0-9a-fA-F]+""")
}

/**
 * Exception messages often carry a path, and a path carries a song title or a disk name:
 * `/Volumes/Band/My Song.mp3 (No such file or directory)`. A path starts after a space, a quote
 * or a bracket (so the `//` of a URL is not one) and ends at ` (`, `: ` or the end of the line,
 * because file names have spaces.
 */
internal fun scrubPaths(text: String?): String? = text?.replace(PATH, "<path>")

private val PATH = Regex("""(?<=^|[\s'"(\[=])(?:file:/+|[A-Za-z]:[\\/]|~?/)[^\n]*?(?=\s\(|:\s|\n|$)""")

/** Also the forms a Windows home takes in URLs and Java paths: `C:/Users/anna`, `file:///c:/Users/anna`. */
internal fun scrubHome(text: String?, home: String? = System.getProperty("user.home")): String? {
    if (text == null || home.isNullOrBlank()) return text
    return listOf(home, home.replace('\\', '/')).distinct().fold(text) { scrubbed, form ->
        scrubbed.replace(form, "~", ignoreCase = true)
    }
}

/** The name Sentry knows: Java says "Mac OS X" on every macOS and "Windows 11" with version 10.0. */
internal fun osFamily(javaName: String?): String = when {
    javaName == null -> "unknown"
    javaName.startsWith("Mac", ignoreCase = true) -> "macOS"
    javaName.startsWith("Windows", ignoreCase = true) -> "Windows"
    else -> javaName
}
