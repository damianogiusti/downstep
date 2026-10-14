package com.damianogiusti.slowdowner.data.library

import java.io.File

/** Rewrites audio containers on disk. Blocking; callers pick the thread. */
interface MediaContainerDataSource {
    /** Copies the audio track of [source] into a plain (non-fragmented) MP4 at [target]. */
    fun remuxToProgressiveMp4(source: File, target: File)
}
