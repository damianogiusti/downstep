package com.damianogiusti.slowdowner.domain.stems

/**
 * What the device says about running the separation. The desktop app binds it from its CPU; an app that
 * does not bind it does not offer the separation at all.
 */
data class StemPlatform(
    /** The separation still runs here but is slow, may fail and is not supported: the user is told before. */
    val isDeprecated: Boolean,
)
