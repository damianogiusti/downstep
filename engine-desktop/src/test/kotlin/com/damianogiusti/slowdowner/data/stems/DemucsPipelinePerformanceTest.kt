package com.damianogiusti.slowdowner.data.stems

import com.damianogiusti.slowdowner.logging.ConsoleLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Time and memory of a whole track, printed and not asserted. Skipped unless
 * `-Dslowdowner.stemModelBin=<.bin>` and `-Dslowdowner.stemFullTrack=<a WAV or any file the engine decodes>` are set.
 * The resident set size is that of the test process (JVM heap and OpenVINO together), read with `ps`: macOS and Linux only.
 */
class DemucsPipelinePerformanceTest {

    private val folder: File = Files.createTempDirectory("demucs-performance-test").toFile()

    @AfterTest
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun residentMegabytes(): Double {
        val process = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString()).start()
        val kilobytes = process.inputStream.bufferedReader().readText().trim().toDouble()
        process.waitFor()
        return kilobytes / 1024
    }

    private fun settledResidentMegabytes(): Double {
        repeat(3) { System.gc(); Thread.sleep(500) }
        return residentMegabytes()
    }

    @Test
    fun `time and memory of a full track are printed`() {
        val bin = System.getProperty("slowdowner.stemModelBin")?.takeIf { it.isNotBlank() }
        val track = System.getProperty("slowdowner.stemFullTrack")?.takeIf { it.isNotBlank() }
        Assume.assumeTrue("slowdowner.stemModelBin and slowdowner.stemFullTrack are not both set", bin != null && track != null)
        Assume.assumeFalse("ps -o rss is not available on Windows", System.getProperty("os.name").startsWith("Windows"))
        val xml = File(requireNotNull(System.getProperty("slowdowner.stemModelXml")))
        val libraries = Path.of(requireNotNull(System.getProperty(OpenVinoRuntime.LIBRARY_DIR_PROPERTY)))
        val source = DemucsStemSeparatorDataSource(ConsoleLogger(), libraries)

        println("PERF rss before: %.0f MB".format(settledResidentMegabytes()))
        for (run in 1..2) {
            val stems = File(folder, "run$run").apply { mkdirs() }
            var peak = residentMegabytes()
            val sampler = Thread {
                while (!Thread.currentThread().isInterrupted) {
                    peak = maxOf(peak, residentMegabytes())
                    try { Thread.sleep(250) } catch (_: InterruptedException) { return@Thread }
                }
            }.apply { isDaemon = true; start() }
            val started = System.nanoTime()
            var lastProgress = 0f
            val outcome = runBlocking {
                source.separate(SeparationRequest(requireNotNull(track), stems, listOf(xml, File(requireNotNull(bin))))) { lastProgress = it }
            }
            val seconds = (System.nanoTime() - started) / 1e9
            sampler.interrupt()
            sampler.join()
            assertIs<SeparationOutcome.Separated>(outcome)
            assertEquals(1f, lastProgress)
            val megabytes = stems.listFiles()!!.sumOf { it.length() } / 1e6
            println("PERF run $run: %.1f s, peak rss %.0f MB, stems on disk %.1f MB, rss after close %.0f MB".format(seconds, peak, megabytes, settledResidentMegabytes()))
        }
    }
}
