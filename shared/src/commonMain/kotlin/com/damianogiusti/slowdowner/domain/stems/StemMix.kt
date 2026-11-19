package com.damianogiusti.slowdowner.domain.stems

/** Gain 0..1 for each stem the track has. The keys are the stems that exist. */
data class StemMix(val gains: Map<StemKind, Float>)
