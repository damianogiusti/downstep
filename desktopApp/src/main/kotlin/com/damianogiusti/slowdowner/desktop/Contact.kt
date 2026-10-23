package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.Logger
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder

/** Where the contact dialog starts, for the logs. */
enum class ContactSource(val logName: String) {
    LIBRARY_MENU("library_menu"),
    MAC_MENU("mac_menu"),
}

// Joined at runtime: this source is public and address harvesters read repositories.
internal val CONTACT_ADDRESS = listOf("contact", "downstep.app").joinToString("@")

/** The subject says which build and OS the message comes from, so a bug report needs fewer questions. */
internal fun contactMailUri(version: String, osName: String, address: String = CONTACT_ADDRESS): URI {
    // URLEncoder is made for forms: it writes a space as "+", which mail apps would show as is.
    val subject = URLEncoder.encode("Downstep $version ($osName)", Charsets.UTF_8).replace("+", "%20")
    return URI("mailto:$address?subject=$subject")
}

/** What the contact dialog does. Failures are warnings: the address stays on screen to copy by hand. */
class ContactActions(
    private val logger: Logger,
    private val version: String,
    private val mail: (URI) -> Unit = { Desktop.getDesktop().mail(it) },
    private val copy: (String) -> Unit = {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(it), null)
    },
) {

    fun opened(source: ContactSource) {
        logger.i("Contact opened", mapOf("source" to source.logName))
    }

    fun copyAddress() {
        logger.i("Contact used", mapOf("action" to "copy"))
        runCatching { copy(CONTACT_ADDRESS) }
            .onFailure { logger.w("Cannot copy the contact address", it) }
    }

    /** False when no mail app could open: the dialog then tells the user to copy the address. */
    fun writeMail(): Boolean {
        logger.i("Contact used", mapOf("action" to "mail"))
        return runCatching { mail(contactMailUri(version, osFamily(System.getProperty("os.name")))) }
            .onFailure { logger.w("Cannot open the mail app", it) }
            .isSuccess
    }
}
