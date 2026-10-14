package com.damianogiusti.slowdowner.data.backup

/**
 * Names inside a `.sldbackup` zip. A backup comes from outside the app: every name that
 * becomes a path on disk is checked here first.
 */
internal object BackupEntryNames {
    const val MANIFEST = "manifest.json"
    private const val AUDIO_DIR = "audio/"
    private const val FALLBACK_EXTENSION = "bin"

    // Library ids are "yt_<videoId>" or "local_<uuid>": letters, digits, '_' and '-' only.
    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val SAFE_EXTENSION = Regex("[A-Za-z0-9]{1,10}")

    fun isSafeId(id: String): Boolean = SAFE_ID.matches(id)

    fun audioEntry(trackId: String, extension: String): String = "$AUDIO_DIR$trackId.$extension"

    /** [raw] if it can be part of a file name, else a neutral one. */
    fun safeExtension(raw: String): String = raw.takeIf(SAFE_EXTENSION::matches) ?: FALLBACK_EXTENSION

    /** The extension if [entry] is exactly `audio/<trackId>.<ext>`, else null. */
    fun audioExtension(trackId: String, entry: String): String? {
        val prefix = "$AUDIO_DIR$trackId."
        if (!entry.startsWith(prefix)) return null
        return entry.removePrefix(prefix).takeIf(SAFE_EXTENSION::matches)
    }

    /** False for absolute paths, parent segments and separators that could escape a folder. */
    fun isSafeEntryName(name: String): Boolean =
        name.isNotEmpty() &&
            !name.startsWith("/") &&
            '\\' !in name &&
            ':' !in name &&
            '\u0000' !in name &&
            name.split('/').none { it == ".." }
}
