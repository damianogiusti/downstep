package com.damianogiusti.slowdowner.data.update

import java.io.File

/** Replaces the running app with a downloaded release. The platform code lives in desktopApp. */
interface UpdateInstaller {
    /** The release file for this computer (Downstep-mac.dmg), or null: the app cannot replace itself here. */
    val assetName: String?

    /**
     * Blocking. Unpacks [download], checks that it is the app at [version], and starts what swaps the
     * apps as soon as this process ends. Throws if anything is off: the installed app stays as it is.
     */
    fun prepare(download: File, version: String)
}
