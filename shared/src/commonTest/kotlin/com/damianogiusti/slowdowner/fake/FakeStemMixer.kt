package com.damianogiusti.slowdowner.fake

import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.domain.stems.StemMixer

class FakeStemMixer : StemMixer {
    /** Every gain set, in order. */
    val calls = mutableListOf<Pair<StemKind, Float>>()

    override fun setGain(stem: StemKind, gain: Float) {
        calls += stem to gain
    }
}
