package com.damianogiusti.slowdowner.domain.export

import com.damianogiusti.slowdowner.domain.loops.Loop
import kotlin.math.roundToInt

/**
 * The names of exported files: safe on every desktop file system (Windows is the strictest), and
 * sorted in the track's order when a folder lists them by name.
 */
object ExportFileNames {
    const val EXTENSION = "mp3"
    const val MAX_TITLE_LENGTH = 80
    private const val FALLBACK_TITLE = "audio"
    private const val FALLBACK_LOOP_NAME = "Loop"
    private val UNSAFE_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F\x7F]""")
    private val WHITESPACE = Regex("""\s+""")

    /** "Title 75%.mp3", plus the pitch when it is not zero: "Title 75% -2st.mp3". */
    fun fullTrack(title: String, speedPercent: Float, pitchSemitones: Float): String {
        val speed = "${speedPercent.roundToInt()}%"
        val semitones = pitchSemitones.roundToInt()
        val pitch = when {
            semitones > 0 -> " +${semitones}st"
            semitones < 0 -> " ${semitones}st"
            else -> ""
        }
        return "${sanitize(title, MAX_TITLE_LENGTH, FALLBACK_TITLE)} $speed$pitch.$EXTENSION"
    }

    /**
     * "<track> - <nn> <loop name>.mp3", e.g. "Song - 03 Solo.mp3". [number] is 1-based in the track's
     * loop order (by start); it has 2 digits, or as many as [count] needs, so the files sort in
     * musical order and two loops with the same name never collide.
     */
    fun loop(title: String, number: Int, count: Int, loopName: String): String {
        val width = maxOf(2, count.toString().length)
        val nn = number.toString().padStart(width, '0')
        val track = sanitize(title, MAX_TITLE_LENGTH, FALLBACK_TITLE)
        return "$track - $nn ${sanitize(loopName, Loop.MAX_NAME_LENGTH, FALLBACK_LOOP_NAME)}.$EXTENSION"
    }

    /**
     * [text] made safe for a file name: characters Windows, macOS or Linux reject become "_", runs
     * of white space (a pasted line break) become one space, and the result is trimmed, at most
     * [maxLength] long, never starts with a dot (a hidden file on macOS and Linux) and never ends in
     * one (Windows drops it). Empty becomes [fallback].
     */
    fun sanitize(text: String, maxLength: Int, fallback: String): String =
        text.replace(WHITESPACE, " ")
            .replace(UNSAFE_CHARS, "_")
            .trim()
            .trimStart('.', ' ')
            .take(maxLength)
            .trimEnd('.', ' ')
            .ifEmpty { fallback }

    /**
     * [names] with a " (2)", " (3)"… before the extension where a name repeats an earlier one,
     * ignoring case (Windows and macOS file systems do).
     */
    fun deduplicate(names: List<String>): List<String> {
        val taken = mutableSetOf<String>()
        return names.map { name ->
            val stem = name.substringBeforeLast('.', name)
            val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            var candidate = name
            var copy = 2
            while (!taken.add(candidate.lowercase())) candidate = "$stem (${copy++})$extension"
            candidate
        }
    }
}
