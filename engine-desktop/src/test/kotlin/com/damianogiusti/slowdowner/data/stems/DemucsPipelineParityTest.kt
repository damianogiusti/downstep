package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.data.playback.NativePcm
import com.damianogiusti.slowdowner.data.playback.PcmDecodeResult
import com.damianogiusti.slowdowner.data.stems.demucs.DemucsModel
import com.damianogiusti.slowdowner.data.stems.demucs.StemFinisher
import com.damianogiusti.slowdowner.data.stems.demucs.StemHeadroom
import com.damianogiusti.slowdowner.data.stems.demucs.TrackNormalisation
import com.damianogiusti.slowdowner.data.stems.demucs.monoMeanAndStd
import com.damianogiusti.slowdowner.data.stems.dsp.Resampler
import com.damianogiusti.slowdowner.domain.stems.StemKind
import com.damianogiusti.slowdowner.logging.ConsoleLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.log10
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The whole Demucs pipeline against PyTorch's Demucs, on the real model. Skipped unless both
 *
 * - `-Dslowdowner.stemModelBin=<htdemucs_fwd.bin>` and
 * - `-Dslowdowner.stemReference=<folder>` are set.
 *
 * The folder is made by a script that runs `demucs.apply.apply_model` (shifts 0, overlap 0.25, split, the
 * whole-track normalisation of `demucs.api.Separator`) on a short excerpt and writes planar little-endian
 * float32 files: `mix.f32` and `<stem>.f32` (drums, bass, other, vocals, guitar, piano), each `[2][N]`;
 * `excerpt.wav` (the excerpt, 44.1 kHz stereo) must be there too. No audio is part of the repository.
 */
class DemucsPipelineParityTest {

    private val folder: File = Files.createTempDirectory("demucs-parity-test").toFile()
    private val reference: File? = System.getProperty("slowdowner.stemReference")?.takeIf { it.isNotBlank() }?.let(::File)
    private val bin: File? = System.getProperty("slowdowner.stemModelBin")?.takeIf { it.isNotBlank() }?.let(::File)
    private val xml = File(requireNotNull(System.getProperty("slowdowner.stemModelXml")))
    private val libraries = Path.of(requireNotNull(System.getProperty(OpenVinoRuntime.LIBRARY_DIR_PROPERTY)))

    @AfterTest
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun requireRealModel() {
        Assume.assumeTrue("slowdowner.stemModelBin and slowdowner.stemReference are not both set", bin != null && reference != null)
    }

    private fun readPlanar(file: File): Array<FloatArray> {
        val floats = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val frames = floats.remaining() / 2
        return Array(2) { channel -> FloatArray(frames).also { floats.position(channel * frames); floats.get(it) } }
    }

    private class Parity(val name: String, val relativeDb: Double, val maxAbsolute: Double, val referenceDb: Double)

    private fun compare(name: String, actual: Array<FloatArray>, expected: Array<FloatArray>, from: Int = 0, to: Int = actual[0].size): Parity {
        var errorEnergy = 0.0
        var referenceEnergy = 0.0
        var worst = 0.0
        for (channel in 0..1) {
            for (i in from until to) {
                val e = (actual[channel][i] - expected[channel][i]).toDouble()
                errorEnergy += e * e
                referenceEnergy += expected[channel][i].toDouble() * expected[channel][i]
                worst = maxOf(worst, abs(e))
            }
        }
        val count = 2.0 * (to - from)
        return Parity(
            name,
            10 * log10(errorEnergy / referenceEnergy.coerceAtLeast(1e-30)),
            worst,
            10 * log10(referenceEnergy / count),
        )
    }

    private fun report(title: String, results: List<Parity>) {
        println("PARITY $title")
        for (r in results) {
            println("PARITY   %-7s relative %7.1f dB   max abs error %.2e   reference level %6.1f dB (power per sample)".format(r.name, r.relativeDb, r.maxAbsolute, r.referenceDb))
        }
    }

    @Test
    fun `the pipeline in floats matches PyTorch Demucs on every stem`() {
        requireRealModel()
        val dir = requireNotNull(reference)
        val mix = readPlanar(File(dir, "mix.f32"))
        val length = mix[0].size
        val normalisation = TrackNormalisation(monoMeanAndStd(mix[0], mix[1]))
        // Gain 1 so that the stems are comparable with the reference; the headroom is tested apart.
        val finisher = StemFinisher(mix, normalisation, gain = 1f)
        val stems = Array(12) { FloatArray(length) }
        var lastEnd = 0
        val started = System.nanoTime()

        runBlocking {
            DemucsNetworkRunner(libraries).run(
                DemucsInput(mix[0], mix[1], normalisation),
                DemucsModelFiles(xml.toPath(), requireNotNull(bin).toPath()),
            ) { start, count, block ->
                assertEquals(lastEnd, start)
                lastEnd = start + count
                finisher.finish(block, start, count)
                for (c in 0 until 12) block[c].copyInto(stems[c], start, 0, count)
            }
        }
        println("PARITY pipeline time for ${length / 44_100.0} s: %.1f s".format((System.nanoTime() - started) / 1e9))
        assertEquals(length, lastEnd)

        val results = mutableListOf<Parity>()
        val expected = DemucsModel.STEMS.associateWith { readPlanar(File(dir, "${it.name.lowercase()}.f32")) }
        for ((index, kind) in DemucsModel.STEMS.withIndex()) {
            val actual = arrayOf(stems[DemucsModel.channelIndex(index, 0)], stems[DemucsModel.channelIndex(index, 1)])
            if (kind == StemKind.OTHER) {
                // Our "other" is the residual: compare with the residual of the reference, and with its own "other".
                val residual = Array(2) { c ->
                    FloatArray(length) { i ->
                        var rest = 0f
                        for (k in DemucsModel.STEMS) if (k != StemKind.OTHER) rest += requireNotNull(expected[k])[c][i]
                        mix[c][i] - rest
                    }
                }
                results += compare("other*", actual, residual)
                results += compare("other", actual, requireNotNull(expected[kind]))
            } else {
                val parity = compare(kind.name.lowercase(), actual, requireNotNull(expected[kind]))
                results += parity
            }
        }
        report("float pipeline vs PyTorch (other* = vs the residual of the reference stems)", results)
        // The network itself differs from PyTorch at -64..-77 dB (OpenVINO's f32 kernels against torch's; the host
        // inputs match to -134 dB), which the transformer amplifies per stem. Loud stems are judged in relative
        // terms, quiet ones in absolute terms. The raw "other" of Demucs is only printed: ours is the residual.
        for (r in results.filter { it.name != "other" }) {
            assertTrue(r.maxAbsolute < 2e-3, "${r.name}: max abs error ${r.maxAbsolute}")
            if (r.referenceDb > -40) assertTrue(r.relativeDb < -50.0, "${r.name}: ${r.relativeDb} dB")
        }
    }

    private fun separateExcerpt(input: File, progress: MutableList<Float> = mutableListOf()): Pair<SeparationOutcome, File> {
        val stems = File(folder, "stems-${input.nameWithoutExtension}").apply { mkdirs() }
        val source = DemucsStemSeparatorDataSource(ConsoleLogger(), libraries)
        val outcome = runBlocking {
            source.separate(SeparationRequest(input.path, stems, listOf(xml, requireNotNull(bin)))) { progress += it }
        }
        return outcome to stems
    }

    private fun decodedStem(file: File): Array<FloatArray> {
        val pcm = assertIs<PcmDecodeResult.Decoded>(NativePcm.decodeStem(file.path)).pcm
        return pcm.use { p ->
            Array(2) { c -> FloatArray(p.frames.toInt()).also { out -> p.copy(c, 0, out) } }
        }
    }

    @Test
    fun `the data source writes six stems that sum to the track and match the reference`() {
        requireRealModel()
        val dir = requireNotNull(reference)
        val mix = readPlanar(File(dir, "mix.f32"))
        val progress = mutableListOf<Float>()

        val (outcome, stemsDir) = separateExcerpt(File(dir, "excerpt.wav"), progress)

        val separated = assertIs<SeparationOutcome.Separated>(outcome)
        assertEquals(StemKind.entries.toSet(), separated.stemFiles.keys)
        assertEquals(setOf("drums.mp3", "bass.mp3", "other.mp3", "vocals.mp3", "guitar.mp3", "piano.mp3"), stemsDir.list()!!.toSet())
        assertEquals(progress.sorted(), progress)
        assertEquals(1f, progress.last())

        val decoded = separated.stemFiles.mapValues { decodedStem(File(stemsDir, it.value)) }
        decoded.values.forEach { assertEquals(mix[0].size, it[0].size) }

        // Sum of the six decoded stems against the headroom times the mix: MP3 error only.
        val sum = Array(2) { c -> FloatArray(mix[0].size) { i -> decoded.values.sumOf { it[c][i].toDouble() }.toFloat() } }
        val scaledMix = Array(2) { c -> FloatArray(mix[0].size) { mix[c][it] * StemHeadroom.GAIN } }
        val sumParity = compare("sum", sum, scaledMix)

        val expected = DemucsModel.STEMS.associateWith { readPlanar(File(dir, "${it.name.lowercase()}.f32")) }
        val perStem = DemucsModel.STEMS.filter { it != StemKind.OTHER }.map { kind ->
            val scaled = Array(2) { c -> FloatArray(mix[0].size) { requireNotNull(expected[kind])[c][it] * StemHeadroom.GAIN } }
            compare(kind.name.lowercase(), requireNotNull(decoded[kind]), scaled)
        }
        report("MP3 stems (192 kbps ABR, -3 dB) vs reference", perStem + sumParity)
        assertTrue(sumParity.relativeDb < -20.0, "sum of the stems: ${sumParity.relativeDb} dB")
    }

    @Test
    fun `a mono file at 48 kHz is resampled and keeps its duration`() {
        requireRealModel()
        val mix = readPlanar(File(requireNotNull(reference), "mix.f32"))
        val seconds = 12
        val mono = FloatArray(seconds * 44_100) { (mix[0][it] + mix[1][it]) / 2 }
        val at48 = Resampler(44_100, 48_000).resample(mono)
        val input = writeWav(folder, "mono48.wav", 48_000, listOf(at48))

        val (outcome, stemsDir) = separateExcerpt(input)

        val separated = assertIs<SeparationOutcome.Separated>(outcome)
        for (name in separated.stemFiles.values) {
            assertEquals(seconds * 44_100, decodedStem(File(stemsDir, name))[0].size)
        }
    }

    @Test
    fun `cancelling after the first segment stops the run, throws and leaves no file`() {
        requireRealModel()
        val dir = requireNotNull(reference)
        val stemsDir = File(folder, "cancelled").apply { mkdirs() }
        File(stemsDir, "vocals.mp3").writeText("earlier separation")
        val source = DemucsStemSeparatorDataSource(ConsoleLogger(), libraries)
        var progressCalls = 0

        assertFailsWith<CancellationException> {
            runBlocking {
                val job = async {
                    source.separate(SeparationRequest(File(dir, "excerpt.wav").path, stemsDir, listOf(xml, requireNotNull(bin)))) {
                        progressCalls++
                        coroutineContextJob?.cancel()
                    }
                }
                coroutineContextJob = job
                job.await()
            }
        }

        assertEquals(1, progressCalls)
        assertEquals(listOf("vocals.mp3"), stemsDir.list()!!.toList())
        assertEquals("earlier separation", File(stemsDir, "vocals.mp3").readText())
    }

    @Volatile
    private var coroutineContextJob: Job? = null
}
