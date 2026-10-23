package com.damianogiusti.slowdowner.data.update

import java.io.File

/** Replaces the running app with a downloaded release. The platform code lives in desktopApp. */
interface UpdateInstaller {
    /** The release file for this computer (Downstep-mac.dmg), or null: the app cannot replace itself here. */
    val assetName: String?

    /**
     * Blocking. Unpacks [download] and checks that it is the app at [version]: nothing changes yet.
     * Throws if anything is off: the installed app stays as it is.
     */
    fun prepare(download: File, version: String)

    /**
     * Starts what swaps the apps as soon as this process ends, for the last [prepare]. Separate from it:
     * the swap waits a limited time for the app to quit, and the user may read the dialog first.
     * Throws if nothing is prepared or it cannot start.
     */
    fun startSwap()
}
