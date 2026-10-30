package com.damianogiusti.slowdowner.data.whatsnew

/** Local-only key-value storage, outside the library: a backup restore must not reset it. Blocking. */
interface WhatsNewStore {
    fun readLastSeenVersion(): String?
    fun writeLastSeenVersion(version: String)
}
