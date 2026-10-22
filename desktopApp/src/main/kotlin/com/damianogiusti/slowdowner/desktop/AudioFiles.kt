package com.damianogiusti.slowdowner.desktop

import java.io.File

/**
 * The files the import accepts. The file dialog filters by these on macOS only: Windows ignores AWT
 * filename filters, so a picked file is checked again before it reaches the library (someone
 * imported a virtual machine image).
 */
internal object AudioFiles {
    // "mpeg": browsers on Windows save audio/mpeg downloads with it, and the decoder plays them.
    val EXTENSIONS = setOf("m4a", "mp3", "wav", "aac", "flac", "aif", "aiff", "caf", "mp4", "mpeg")

    fun isImportable(file: File) = file.extension.lowercase() in EXTENSIONS

    /** The Windows dialog filters by the name pattern it starts with: `*.m4a;*.mp3;...`. */
    fun windowsPattern(extensions: Set<String>) = extensions.joinToString(";") { "*.$it" }

    /** For the log: the extension says which formats people try, the name stays on the computer. */
    fun logExtension(file: File) = file.extension.lowercase().take(MAX_LOGGED_EXTENSION).ifEmpty { "none" }

    private const val MAX_LOGGED_EXTENSION = 8
}
