package com.damianogiusti.slowdowner.data.whatsnew

/** The changelog file that comes with the app. Blocking. */
interface ChangelogDataSource {
    /** Null if the file is not there or cannot be read. */
    fun read(): String?
}
