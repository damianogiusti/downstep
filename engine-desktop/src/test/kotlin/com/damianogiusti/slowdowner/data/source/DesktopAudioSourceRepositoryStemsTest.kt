package com.damianogiusti.slowdowner.data.source

import com.damianogiusti.slowdowner.data.playback.DesktopPlaybackEngine
import com.damianogiusti.slowdowner.data.playback.NativeEngine
import com.damianogiusti.slowdowner.data.playback.NativeStemWriter
import com.damianogiusti.slowdowner.data.playback.StemFormat
import com.damianogiusti.slowdowner.data.playback.StemWriterOpenResult
import com.damianogiusti.slowdowner.data.stems.writeWav
import com.damianogiusti.slowdowner.domain.source.AudioSource
import com.damianogiusti.slowdowner.domain.source.OpenResult
import com.damianogiusti.slowdowner.domain.source.StemSource
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The stems through the real engine in manual mode: nothing plays, the engine only reports its gains. */
class DesktopAudioSourceRepositoryStemsTest {

    private val native = NativeEngine.manual(RATE)
    private val folder = createTempDirectory("source-stems").toFile()
    private val logged = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        native.close()
        folder.deleteRecursively()
    }

    @Test
    fun `when stems are opened then the engine has one source per stem and the repository reports them`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val source = stemsSource(StemKind.VOCALS, StemKind.DRUMS, StemKind.BASS)

        val repository = repository(engine)

        assertEquals(OpenResult.Opened, repository.open(source))

        assertEquals(3, native.stemCount)
        assertEquals(source, repository.current())
    }

    @Test
    fun `a gain goes to the engine index of the stem in the order of the source`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val repository = repository(engine)
        // Not the order of the enum: the index follows the list, not the kind.
        repository.open(stemsSource(StemKind.OTHER, StemKind.BASS, StemKind.VOCALS))

        repository.setGain(StemKind.VOCALS, 0.25f)
        repository.setGain(StemKind.OTHER, 0f)

        assertEquals(listOf(0f, 1f, 0.25f), engine.stemGains)
    }

    @Test
    fun `a gain for a stem the source lacks does nothing`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val repository = repository(engine)
        repository.open(stemsSource(StemKind.VOCALS, StemKind.DRUMS))

        repository.setGain(StemKind.PIANO, 0f)

        assertEquals(listOf(1f, 1f), engine.stemGains)
    }

    @Test
    fun `when the open source is a single file then the mixer does nothing`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val repository = repository(engine)
        val file = writeWav(folder, "song.wav", RATE, listOf(FloatArray(RATE), FloatArray(RATE)))
        assertEquals(OpenResult.Opened, repository.open(AudioSource.LocalFile(file.path)))

        repository.setGain(StemKind.VOCALS, 0f)

        assertTrue(engine.stemGains.isEmpty())
    }

    @Test
    fun `after a switch from stems to the file the old stem order is forgotten`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val repository = repository(engine)
        repository.open(stemsSource(StemKind.VOCALS, StemKind.DRUMS))
        val file = writeWav(folder, "song.wav", RATE, listOf(FloatArray(RATE), FloatArray(RATE)))
        repository.open(AudioSource.LocalFile(file.path))

        repository.setGain(StemKind.VOCALS, 0f)

        assertTrue(engine.stemGains.isEmpty())
    }

    @Test
    fun `when a stem file is missing then the open fails with a warning that names no file`() = runTest {
        val engine = DesktopPlaybackEngine(native, backgroundScope)
        val repository = repository(engine)
        val missing = AudioSource.Stems(
            listOf(StemSource(StemKind.VOCALS, File(folder, "gone.wav").path)),
        )

        assertEquals(OpenResult.Failed, repository.open(missing))

        assertFalse(engine.isOpen)
        assertEquals(1, logged.size)
        assertTrue(logged.single().startsWith("W Cannot open track"), logged.single())
        assertFalse("gone" in logged.single() || folder.name in logged.single(), logged.single())
    }

    private fun repository(engine: DesktopPlaybackEngine) = DesktopAudioSourceRepository(engine, LineLogger(logged))

    private fun stemsSource(vararg kinds: StemKind) = AudioSource.Stems(
        kinds.map { kind ->
            StemSource(kind, stemFile(kind).path)
        },
    )

    // The engine decodes stems as the pipeline writes them: MP3.
    private fun stemFile(kind: StemKind): File {
        val file = File(folder, "${kind.name}.mp3")
        val opened = NativeStemWriter.open(file.path, StemFormat(RATE, channels = 2)) as StemWriterOpenResult.Opened
        val writer = opened.writer
        assertTrue(writer.write(FloatArray(RATE * 2)))
        writer.close()
        assertTrue(writer.isComplete)
        return file
    }

    private class LineLogger(private val lines: MutableList<String>) : Logger {
        override fun d(message: String, properties: Map<String, String>) { lines += "D $message $properties" }
        override fun i(message: String, properties: Map<String, String>) { lines += "I $message $properties" }
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) { lines += "W $message $properties" }
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) { lines += "E $message $properties" }
    }

    private companion object {
        const val RATE = 44_100
    }
}
