package com.damianogiusti.slowdowner.data.stems

import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assume
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The OpenVINO binding on a tiny IR model (two inputs, two outputs, each an Add with a weight read from
 * the .bin), so it runs on every CI platform without the 74 MB Demucs weights. Every native call runs
 * here: they are `invokeExact` calls, and a wrong type only fails at run time.
 */
class OpenVinoRuntimeTest {

    private val folder: Path = Files.createTempDirectory("openvino-test")

    @AfterTest
    fun tearDown() {
        folder.toFile().deleteRecursively()
    }

    private val xWeights = floatArrayOf(10f, 20f, 30f, 40f, 50f, 60f)
    private val xtWeights = floatArrayOf(0.5f, 0.25f, 0.125f, 0.0625f)

    private fun tinyModel(): Pair<Path, Path> {
        val xml = folder.resolve("tiny.xml")
        Files.copy(requireNotNull(javaClass.getResourceAsStream("/openvino/tiny_two_adds.xml")) { "test resource missing" }, xml)
        val bytes = ByteBuffer.allocate((xWeights.size + xtWeights.size) * 4).order(ByteOrder.LITTLE_ENDIAN)
        xWeights.forEach { bytes.putFloat(it) }
        xtWeights.forEach { bytes.putFloat(it) }
        val bin = folder.resolve("tiny.bin")
        Files.write(bin, bytes.array())
        return xml to bin
    }

    private fun withModel(block: (OpenVinoModel) -> Unit) {
        val (xml, bin) = tinyModel()
        OpenVinoRuntime.load().use { runtime -> runtime.loadModel(xml, bin).use(block) }
    }

    @Test
    fun `when a tiny model runs then both outputs are the inputs plus the weights`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        model.setInput(0, floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f))
        model.setInput(1, floatArrayOf(1f, 1f, 1f, 1f))
        model.infer()

        assertEquals(6L, model.outputSize(0))
        assertEquals(4L, model.outputSize(1))
        val x = FloatArray(6).also { model.copyOutput(0, it) }
        val xt = FloatArray(4).also { model.copyOutput(1, it) }
        assertContentEquals(floatArrayOf(11f, 22f, 33f, 44f, 55f, 66f), x)
        assertContentEquals(floatArrayOf(1.5f, 1.25f, 1.125f, 1.0625f), xt)
    }

    @Test
    fun `when the model runs twice then the second run sees the new input`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        model.setInput(0, FloatArray(6))
        model.setInput(1, FloatArray(4))
        model.infer()
        model.setInput(0, FloatArray(6) { 100f })
        model.setInput(1, FloatArray(4) { -1f })
        model.infer()

        assertContentEquals(floatArrayOf(110f, 120f, 130f, 140f, 150f, 160f), FloatArray(6).also { model.copyOutput(0, it) })
        assertContentEquals(floatArrayOf(-0.5f, -0.75f, -0.875f, -0.9375f), FloatArray(4).also { model.copyOutput(1, it) })
    }

    @Test
    fun `when an input comes from native memory then it is copied like an array`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        Arena.ofConfined().use { arena ->
            val native = arena.allocate(6L * 4)
            repeat(6) { native.setAtIndex(JAVA_FLOAT, it.toLong(), 1f) }
            model.setInput(0, native, 6)
        }
        model.setInput(1, FloatArray(4))
        model.infer()

        assertContentEquals(floatArrayOf(11f, 21f, 31f, 41f, 51f, 61f), FloatArray(6).also { model.copyOutput(0, it) })
    }

    @Test
    fun `when a model loads then it is compiled in f32 and no input is bound yet`() {
        // loadModel reads INFERENCE_PRECISION_HINT back and throws if the CPU kept f16: getting a model is the assertion.
        withModel { model -> assertEquals(emptyList(), model.inputSizes) }
    }

    @Test
    fun `when inputs are bound then their sizes are the products of the shapes`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        assertEquals(listOf(6L, 4L), model.inputSizes)
    }

    @Test
    fun `when an input has the wrong size then it is refused`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        assertFailsWith<IllegalArgumentException> { model.setInput(0, FloatArray(5)) }
    }

    @Test
    fun `when an output array has the wrong size then it is refused`() = withModel { model ->
        model.bindInputs(listOf(longArrayOf(1, 2, 3), longArrayOf(1, 4)))
        model.infer()
        assertFailsWith<IllegalArgumentException> { model.copyOutput(0, FloatArray(5)) }
    }

    @Test
    fun `when the model file is missing then the exception has the status and no path`() {
        val missing = folder.resolve("secret-name.xml")
        OpenVinoRuntime.load().use { runtime ->
            val error = assertFailsWith<OpenVinoException> { runtime.loadModel(missing, folder.resolve("secret-name.bin")) }
            assertNotNull(error.status)
            assertTrue(!error.message.orEmpty().contains("secret-name"), error.message)
        }
    }

    @Test
    fun `when the library folder is missing then loading throws OpenVinoException without a status`() {
        val error = assertFailsWith<OpenVinoException> { OpenVinoRuntime.load(folder.resolve("nothing")) }
        assertNull(error.status)
    }

    @Test
    fun `when the runtime closes then its models close with it`() {
        val (xml, bin) = tinyModel()
        val runtime = OpenVinoRuntime.load()
        val model = runtime.loadModel(xml, bin)
        runtime.close()
        assertFailsWith<IllegalStateException> { model.infer() }
        runtime.close() // twice is fine
    }

    /** Needs the 74 MB Demucs weights, which the app downloads: run with -Dslowdowner.stemModelBin=<.bin>. */
    @Test
    fun `when the real Demucs model runs one segment then the outputs are finite and shaped right`() {
        val binPath = System.getProperty("slowdowner.stemModelBin")?.takeIf { it.isNotBlank() }
        // Reported as skipped, not as passed, when the property is not set.
        Assume.assumeTrue("slowdowner.stemModelBin is not set", binPath != null)
        val bin = File(requireNotNull(binPath))
        val xml = File(requireNotNull(System.getProperty("slowdowner.stemModelXml")) { "Gradle sets slowdowner.stemModelXml" })
        OpenVinoRuntime.load().use { runtime ->
            runtime.loadModel(xml.toPath(), bin.toPath()).use { model ->
                model.bindInputs(listOf(longArrayOf(1, 4, 2048, 336), longArrayOf(1, 2, 343_980)))
                val random = java.util.Random(7)
                model.setInput(0, FloatArray(4 * 2048 * 336) { random.nextFloat() * 2 - 1 })
                model.setInput(1, FloatArray(2 * 343_980) { random.nextFloat() * 2 - 1 })
                model.infer()
                assertEquals(24L * 2048 * 336, model.outputSize(0))
                assertEquals(12L * 343_980, model.outputSize(1))
                val x = FloatArray(24 * 2048 * 336).also { model.copyOutput(0, it) }
                val xt = FloatArray(12 * 343_980).also { model.copyOutput(1, it) }
                assertTrue(x.all { it.isFinite() }, "x_out has a non-finite value")
                assertTrue(xt.all { it.isFinite() }, "xt_out has a non-finite value")
            }
        }
    }
}
