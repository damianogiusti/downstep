package com.damianogiusti.slowdowner.domain.whatsnew

/**
 * The gate for one popup per launch: while this is true the support prompt and the update dialog stay
 * closed. The update banner is not a popup, so it stays.
 */
class IsWhatsNewDueUseCase(private val getWhatsNew: GetWhatsNewUseCase) {
    suspend operator fun invoke(): Boolean = getWhatsNew().isNotEmpty()
}
