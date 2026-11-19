package com.damianogiusti.slowdowner.data.stems

/** A model and the files that make it: the platform that uses it decides which one (see its DI module). */
data class StemModelSpec(val id: String, val files: List<ModelFileSpec>)

data class ModelFileSpec(val name: String, val url: String, val sizeBytes: Long, val sha256: String)
