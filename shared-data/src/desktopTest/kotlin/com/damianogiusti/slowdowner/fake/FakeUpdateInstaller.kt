package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.data.update.UpdateInstaller
import java.io.File

class FakeUpdateInstaller(override val assetName: String? = "Downstep-mac.dmg") : UpdateInstaller {
    var error: Exception? = null
    val prepared = mutableListOf<Pair<ByteArray, String>>()

    override fun prepare(download: File, version: String) {
        error?.let { throw it }
        prepared += download.readBytes() to version
    }
}
