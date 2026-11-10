package com.damianogiusti.slowdowner.domain.source

sealed interface OpenResult {
    data object Opened : OpenResult
    /** The decoded track would not fit in memory: a shorter file can open, so the user can act. */
    data object TooLong : OpenResult
    /** Missing, unreadable or not decodable. */
    data object Failed : OpenResult
}
