package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.logging.Logger
import com.damianogiusti.slowdowner.ui.support.DONATE_URL
import java.awt.Desktop
import java.net.URI

/** Where a donation starts, for the logs. The prompt's own Donate button is logged as a close action. */
enum class DonateSource(val logName: String) {
    LIBRARY_MENU("library_menu"),
    MAC_MENU("mac_menu"),
}

/** Opens PayPal in the browser. */
fun openDonation(
    source: DonateSource,
    logger: Logger,
    browse: (URI) -> Unit = { Desktop.getDesktop().browse(it) },
) {
    logger.i("Donate opened", mapOf("source" to source.logName))
    runCatching { browse(URI(DONATE_URL)) }
        .onFailure { logger.w("Cannot open the donation page", it) }
}
