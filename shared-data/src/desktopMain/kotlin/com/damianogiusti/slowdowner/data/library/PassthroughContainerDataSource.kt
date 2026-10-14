package com.damianogiusti.slowdowner.data.library

import java.io.File

/**
 * The desktop engine decodes the whole file into memory before playing, so fragmented MP4 seeks as
 * fast as a plain one: no remux needed, the file is kept as downloaded.
 */
class PassthroughContainerDataSource : MediaContainerDataSource {
    override fun remuxToProgressiveMp4(source: File, target: File) {
        source.copyTo(target, overwrite = true)
    }
}
