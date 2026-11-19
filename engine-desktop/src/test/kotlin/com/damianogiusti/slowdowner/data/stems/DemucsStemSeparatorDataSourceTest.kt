package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.logging.Logger
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The failure paths of the Demucs data source, which need the engine and OpenVINO but not the 74 MB weights.
 * The run with the real model is in [DemucsPipelineParityTest].
 */
class DemucsStemSeparatorDataSourceTest {

    private val folder: File = Files.createTempDirectory("demucs-source-test").toFile()
    private val output = File(folder, "stems").apply { mkdirs() }
    private val logger = SilentLogger()
    private val source = DemucsStemSeparatorDataSource(logger, Path.of(requireNotNull(System.getProperty(OpenVinoRuntime.LIBRARY_DIR_PROPERTY))))

    @AfterTest
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun fakeModelFiles(): List<File> = listOf(
        File(folder, "model.xml").apply { writeText("<net/>") },
        File(folder, "model.bin").apply { writeBytes(ByteArray(16)) },
    )

    private fun tone(frames: Int) = FloatArray(frames) { (0.3 * sin(2 * PI * 440 * it / 44_100)).toFloat() }

    private fun separate(input: File, modelFiles: List<File>): SeparationOutcome =
        runBlocking { source.separate(SeparationRequest(input.path, output, modelFiles)) {} }

    @Test
    fun `when a model file is missing then the result is model failed`() {
        val input = writeWav(folder, "in.wav", 44_100, listOf(tone(44_100)))

        val outcome = separate(input, listOf(File(folder, "model.xml")))

        assertEquals(SeparationOutcome.Failed(SeparationFailure.MODEL_FAILED), outcome)
        assertTrue(output.list().isNullOrEmpty())
    }

    @Test
    fun `when the input is not audio then the result is decode failed`() {
        val input = File(folder, "notes.txt").apply { writeText("not audio") }

        val outcome = separate(input, fakeModelFiles())

        assertEquals(SeparationOutcome.Failed(SeparationFailure.DECODE_FAILED), outcome)
        assertTrue(output.list().isNullOrEmpty())
    }

    @Test
    fun `when the input is a single sample then the result is decode failed`() {
        val input = writeWav(folder, "one.wav", 44_100, listOf(floatArrayOf(0.1f)))

        assertEquals(SeparationOutcome.Failed(SeparationFailure.DECODE_FAILED), separate(input, fakeModelFiles()))
    }

    @Test
    fun `when the model cannot be read then the result is model failed and no file is left`() {
        val input = writeWav(folder, "in.wav", 48_000, listOf(tone(48_000), tone(48_000)))
        File(output, "drums.mp3").writeText("earlier separation")

        val outcome = separate(input, fakeModelFiles())

        assertEquals(SeparationOutcome.Failed(SeparationFailure.MODEL_FAILED), outcome)
        // Files of an earlier separation are untouched; the .part files of this run are gone.
        assertEquals(listOf("drums.mp3"), output.list()!!.toList())
        assertEquals("earlier separation", File(output, "drums.mp3").readText())
        assertTrue(logger.warnings.single().contains("MODEL_FAILED"))
    }

    @Test
    fun `when the output folder is gone then the result is write failed`() {
        val input = writeWav(folder, "in.wav", 44_100, listOf(tone(44_100)))
        val missing = File(folder, "missing")

        val outcome = runBlocking { source.separate(SeparationRequest(input.path, missing, fakeModelFiles())) {} }

        assertIs<SeparationOutcome.Failed>(outcome)
        assertEquals(SeparationFailure.WRITE_FAILED, outcome.reason)
    }

    private class SilentLogger : Logger {
        val warnings = mutableListOf<String>()
        override fun d(message: String, properties: Map<String, String>) = Unit
        override fun i(message: String, properties: Map<String, String>) = Unit
        override fun w(message: String, throwable: Throwable?, properties: Map<String, String>) {
            warnings += "$message $properties"
        }
        override fun e(message: String, throwable: Throwable?, properties: Map<String, String>) {
            warnings += "ERROR $message $properties"
        }
    }
}
