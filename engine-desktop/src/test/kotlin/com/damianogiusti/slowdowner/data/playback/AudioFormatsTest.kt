package com.damianogiusti.slowdowner.data.playback

import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The formats the app imports, through the OS decoder (AudioToolbox on macOS, Media Foundation on
 * Windows). Each file is a 3 s, 441 Hz stereo tone made with ffmpeg. The folder name has accents:
 * user folders often do, and Windows needs wide-character paths for them.
 */
class AudioFormatsTest {

    private val native = NativeEngine.manual(RATE)
    private val folder = Files.createTempDirectory("Nicolò è qui").toFile()

    @AfterTest
    fun tearDown() {
        native.close()
        folder.deleteRecursively()
    }

    /** YouTube's DASH audio, as the desktop keeps it after an import. */
    @Test
    fun `a fragmented DASH m4a decodes`() = assertPlays("fragmented-dash.m4a")

    /** What Android's import writes, and what restored backups bring. */
    @Test
    fun `a progressive m4a decodes`() = assertPlays("progressive.m4a")

    @Test
    fun `an mp3 decodes`() = assertPlays("tone.mp3")

    @Test
    fun `an export writes to a path with accents`() {
        assertTrue(native.open(fixture("progressive.m4a").path))
        val output = File(folder, "Più lento.mp3")
        val export = requireNotNull(native.beginExport(output.path)) { "export did not start" }
        while (export.next() != null) Unit
        assertTrue(export.finish())
        assertTrue(output.length() > 10_000, "size ${output.length()}")
    }

    private fun assertPlays(name: String) {
        val file = fixture(name)
        assertTrue(native.open(file.path), "$name did not open")
        // AAC adds a few ms of encoder delay that not every decoder trims.
        assertTrue(abs(native.durationSeconds - 3.0) < 0.1, "$name lasts ${native.durationSeconds} s")
        native.setPlaying(true)
        val audio = native.render(RATE)
        // ffmpeg's tone is quiet (peak about 0.09 out of the engine): enough to tell it from silence.
        assertTrue(audio.count { abs(it) > 0.02f } > audio.size / 2, "$name plays silence, peak ${audio.maxOf { abs(it) }}")
    }

    private fun fixture(name: String): File {
        val target = File(folder, name)
        requireNotNull(javaClass.getResourceAsStream("/formats/$name")) { "missing fixture $name" }
            .use { input -> target.outputStream().use(input::copyTo) }
        return target
    }

    private companion object {
        const val RATE = 44_100
    }
}
