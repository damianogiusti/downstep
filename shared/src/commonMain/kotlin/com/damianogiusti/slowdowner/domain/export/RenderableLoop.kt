package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.playback.PlaybackEngine

/** An active loop with room in it: only that can be rendered on its own as a [RenderRange]. */
internal fun PlaybackEngine.hasRenderableLoop() = isLoopOn && loopStart >= 0f && loopEnd <= 1f && loopStart < loopEnd
