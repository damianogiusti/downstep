package com.damianogiusti.slowdowner.logging

/**
 * Injected wherever something worth knowing happens: a failure the user only sees as a generic
 * message, or a rare event that tells how the app is used. Never per frame, per click or per
 * progress step.
 *
 * Properties must have few possible values (format, source, stage) and no personal data: no
 * titles, no file names, no paths. The only exception is the YouTube link of a failed import.
 */
interface Logger {
    /** Development only: never leaves the computer. */
    fun d(message: String, properties: Map<String, String> = emptyMap())

    fun i(message: String, properties: Map<String, String> = emptyMap())

    /** Something went wrong, but it is not a bug of ours (a bad file, no network). */
    fun w(message: String, throwable: Throwable? = null, properties: Map<String, String> = emptyMap())

    /** A bug to fix: with a [throwable] it becomes an issue in the crash reports. */
    fun e(message: String, throwable: Throwable? = null, properties: Map<String, String> = emptyMap())
}
