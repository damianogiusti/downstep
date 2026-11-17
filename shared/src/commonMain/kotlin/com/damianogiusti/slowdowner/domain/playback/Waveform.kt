package com.damianogiusti.slowdowner.domain.playback

/** Peak levels 0..1 of [from]..[to] (fractions of the file), one per equal slice. */
class Waveform(val from: Float, val to: Float, val levels: FloatArray)
