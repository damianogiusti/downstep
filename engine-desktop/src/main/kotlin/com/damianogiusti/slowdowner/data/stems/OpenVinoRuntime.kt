package com.damianogiusti.slowdowner.data.stems

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.nio.file.Files
import java.nio.file.Path

/**
 * What went wrong in OpenVINO, or while loading it. This is the error model of the binding: every call
 * that can fail throws it, with OpenVINO's own text and no path in the message. The repository that
 * will use the runtime maps it to its sealed result; nothing else leaves it.
 *
 * [status] is the `ov_status_e` of the failed call, or null if no C call failed (a missing library, a
 * precision that was not applied, a buffer of the wrong size).
 */
class OpenVinoException(message: String, val status: Int? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * OpenVINO's C API through FFM, for the Demucs conversion that splits a track into stems. A thin
 * wrapper in the style of [com.damianogiusti.slowdowner.data.playback.NativeEngine]: one [Api] per
 * loaded library, `invokeExact` everywhere, an [Arena] per call.
 *
 * The libraries are the five that [load] finds in one folder: the C API, the core, the IR reader, the
 * CPU plugin and oneTBB. OpenVINO finds the reader and the plugin next to the core by itself.
 * Only the IR format is readable (no ONNX frontend is shipped).
 *
 * Not thread-safe: one thread at a time per runtime and per [OpenVinoModel]. [close] closes the models
 * that are still open.
 */
class OpenVinoRuntime private constructor(private val api: Api, private val core: MemorySegment) : AutoCloseable {

    private val models = mutableSetOf<OpenVinoModel>()
    private var isClosed = false

    /**
     * Reads the model ([xml] and its weights [bin]), compiles it for the CPU with the inference
     * precision forced to f32 and creates an infer request. Blocking: compiling takes seconds.
     *
     * f32 is not the default on Apple silicon, where OpenVINO picks f16 and quiet stems come out ruined.
     * The precision is read back from the compiled model: a runtime that ignored it throws here.
     */
    fun loadModel(xml: Path, bin: Path): OpenVinoModel {
        check(!isClosed) { "runtime already closed" }
        val model = Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            api.check(
                api.readModel.invokeExact(core, arena.allocateFrom(xml.toString()), arena.allocateFrom(bin.toString()), out) as Int,
                "read the model",
            )
            out.get(ADDRESS, 0)
        }
        try {
            return OpenVinoModel.create(api, core, model, onClose = { models -= it }).also { models += it }
        } catch (e: Throwable) {
            api.modelFree.invokeExact(model)
            throw e
        }
    }

    @Synchronized
    override fun close() {
        if (isClosed) return
        isClosed = true
        models.toList().forEach { it.close() }
        api.coreFree.invokeExact(core)
    }

    companion object {
        /** System property with the folder of the OpenVINO libraries. Set by Gradle for tests. */
        const val LIBRARY_DIR_PROPERTY = "slowdowner.openvino.dir"

        // Windows does not look for a DLL's imports in the DLL's own folder, and neither does it search
        // the folder of the DLL that OpenVINO loads later. Loading them by full path first, imports before
        // the DLLs that need them, puts every one in the process by name: from then on they resolve.
        // tbb12 needs nothing of ours; openvino needs tbb12; the reader and the plugin need openvino.
        // openvino_c, which holds the symbols we call, goes last. (The MSVC runtime they also need is
        // already in the process: the JVM's own.)
        private val WINDOWS_PRELOAD = listOf("tbb12.dll", "openvino.dll", "openvino_ir_frontend.dll", "openvino_intel_cpu_plugin.dll")

        /**
         * Loads the libraries from [libraryDir] and creates an OpenVINO core. The libraries stay loaded for
         * the life of the process, like the engine's.
         * @throws OpenVinoException if a library is missing or does not load.
         */
        fun load(libraryDir: Path = defaultLibraryDir()): OpenVinoRuntime {
            val api = Api.load(libraryDir)
            return Arena.ofConfined().use { arena ->
                val out = arena.allocate(ADDRESS)
                api.check(api.coreCreate.invokeExact(out) as Int, "create the core")
                OpenVinoRuntime(api, out.get(ADDRESS, 0))
            }
        }

        private fun defaultLibraryDir(): Path {
            val path = System.getProperty(LIBRARY_DIR_PROPERTY)
            requireNotNull(path) { "set -D$LIBRARY_DIR_PROPERTY=<folder of the OpenVINO libraries>" }
            return Path.of(path)
        }

        private fun findC(libraryDir: Path): Path {
            if (!Files.isDirectory(libraryDir)) throw OpenVinoException("the folder of the OpenVINO libraries is missing")
            // libopenvino_c.2641.dylib, openvino_c.dll
            return Files.list(libraryDir).use { files ->
                files.filter { it.fileName.toString().let { name -> name.startsWith("libopenvino_c.") || name == "openvino_c.dll" } }
                    .findFirst().orElse(null)
            } ?: throw OpenVinoException("the C API library of OpenVINO is missing")
        }
    }

    /** The C API: one instance per folder, kept for the life of the process. */
    internal class Api(private val lookup: SymbolLookup) {
        private val linker = Linker.nativeLinker()
        private fun function(name: String, result: MemoryLayout?, vararg args: MemoryLayout, options: Array<Linker.Option> = emptyArray()) =
            linker.downcallHandle(
                lookup.find(name).orElseThrow { OpenVinoException("missing native symbol $name") },
                if (result == null) FunctionDescriptor.ofVoid(*args) else FunctionDescriptor.of(result, *args),
                *options,
            )

        val errorInfo = function("ov_get_error_info", ADDRESS, JAVA_INT)
        val lastErrorMessage = function("ov_get_last_err_msg", ADDRESS)
        val free = function("ov_free", null, ADDRESS)
        val coreCreate = function("ov_core_create", JAVA_INT, ADDRESS)
        val coreFree = function("ov_core_free", null, ADDRESS)
        val readModel = function("ov_core_read_model", JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
        val modelFree = function("ov_model_free", null, ADDRESS)

        // Variadic: core, model, device, argument count, out, then key and value strings. The count is
        // the number of arguments after it (two per property), not of pairs: a wrong one silently drops
        // properties. Version 2025.4.1 has no ov_core_compile_model_props to avoid this.
        val compileModel = function(
            "ov_core_compile_model", JAVA_INT,
            ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
            options = arrayOf(Linker.Option.firstVariadicArg(5)),
        )
        val compiledModelFree = function("ov_compiled_model_free", null, ADDRESS)
        val compiledModelGetProperty = function("ov_compiled_model_get_property", JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
        val createInferRequest = function("ov_compiled_model_create_infer_request", JAVA_INT, ADDRESS, ADDRESS)
        val inferRequestFree = function("ov_infer_request_free", null, ADDRESS)
        val setInputTensor = function("ov_infer_request_set_input_tensor_by_index", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS)
        val infer = function("ov_infer_request_infer", JAVA_INT, ADDRESS)
        val getOutputTensor = function("ov_infer_request_get_output_tensor_by_index", JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS)

        // ov_shape_t, passed by value: { int64_t rank; int64_t *dims; }
        val tensorFromHostPtr = function("ov_tensor_create_from_host_ptr", JAVA_INT, JAVA_INT, SHAPE, ADDRESS, ADDRESS)
        val tensorData = function("ov_tensor_data", JAVA_INT, ADDRESS, ADDRESS)
        val tensorByteSize = function("ov_tensor_get_byte_size", JAVA_INT, ADDRESS, ADDRESS)
        val tensorFree = function("ov_tensor_free", null, ADDRESS)

        /** Turns a non-zero `ov_status_e` into an [OpenVinoException] with OpenVINO's text. */
        fun check(status: Int, what: String) {
            if (status == 0) return
            val info = (errorInfo.invokeExact(status) as MemorySegment).text()
            val last = (lastErrorMessage.invokeExact() as MemorySegment).text()
            // OpenVINO's messages can quote the model's path: keep only the first line, which names the error.
            throw OpenVinoException("OpenVINO failed to $what: $info (status $status) ${last.lineSequence().firstOrNull().orEmpty()}".trim(), status)
        }

        private fun MemorySegment.text(): String =
            if (this == MemorySegment.NULL) "" else reinterpret(Long.MAX_VALUE).getString(0)

        companion object {
            val SHAPE: MemoryLayout = MemoryLayout.structLayout(JAVA_LONG.withName("rank"), ADDRESS.withName("dims"))
            private val loaded = mutableMapOf<Path, Api>()

            @Synchronized
            fun load(libraryDir: Path): Api = loaded.getOrPut(libraryDir.toAbsolutePath()) {
                val dir = libraryDir.toAbsolutePath()
                for (name in WINDOWS_PRELOAD) {
                    val dll = dir.resolve(name)
                    if (Files.exists(dll)) loadByName(dll)
                }
                val c = findC(dir)
                val lookup = try {
                    SymbolLookup.libraryLookup(c, Arena.global())
                } catch (e: IllegalArgumentException) {
                    // FFM only says "Cannot open library": System.load gives the system's reason (a missing
                    // dependency, a wrong architecture).
                    val reason = runCatching { System.load(c.toString()) }.exceptionOrNull()
                    throw OpenVinoException("cannot load the OpenVINO C API: ${reason?.message ?: e.message}", cause = reason ?: e)
                }
                Api(lookup)
            }

            private fun loadByName(library: Path) {
                try {
                    System.load(library.toString())
                } catch (e: UnsatisfiedLinkError) {
                    throw OpenVinoException("cannot load ${library.fileName}: ${e.message}", cause = e)
                }
            }
        }
    }
}

/**
 * A compiled model with its infer request. Inputs are f32 tensors in native memory that this object
 * owns: [bindInputs] sizes them once, [setInput] fills them for each run, [infer] runs, [copyOutput]
 * reads the results. Created by [OpenVinoRuntime.loadModel]; [close] frees the tensors, the request and
 * the model.
 */
class OpenVinoModel private constructor(
    private val api: OpenVinoRuntime.Api,
    private val model: MemorySegment,
    private val compiled: MemorySegment,
    private val request: MemorySegment,
    private val onClose: (OpenVinoModel) -> Unit,
) : AutoCloseable {

    // Shared, not confined: the owner of the model may close it from another thread than the one that ran it.
    private val buffers = Arena.ofShared()
    private val inputBuffers = mutableListOf<MemorySegment>()
    private val inputTensors = mutableListOf<MemorySegment>()
    private var isClosed = false

    /** Number of floats of each input, in order. Empty until [bindInputs]. */
    val inputSizes: List<Long> get() = inputBuffers.map { it.byteSize() / FLOAT_BYTES }

    /**
     * Creates the f32 input tensors, one per shape in the order of the model's inputs, zero filled, and
     * gives them to the request. Calling it again replaces them.
     */
    fun bindInputs(shapes: List<LongArray>) {
        check(!isClosed) { "model already closed" }
        inputTensors.forEach { api.tensorFree.invokeExact(it) }
        inputTensors.clear()
        inputBuffers.clear()
        Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            shapes.forEachIndexed { index, dims ->
                val floats = dims.fold(1L) { total, dim -> total * dim }
                val buffer = buffers.allocate(floats * FLOAT_BYTES, 64)
                val dimsSegment = arena.allocate(JAVA_LONG.byteSize() * dims.size, 8)
                dims.forEachIndexed { i, dim -> dimsSegment.setAtIndex(JAVA_LONG, i.toLong(), dim) }
                val shape = arena.allocate(OpenVinoRuntime.Api.SHAPE)
                shape.set(JAVA_LONG, 0, dims.size.toLong())
                shape.set(ADDRESS, JAVA_LONG.byteSize(), dimsSegment)
                api.check(api.tensorFromHostPtr.invokeExact(ELEMENT_TYPE_F32, shape, buffer, out) as Int, "create input $index")
                val tensor = out.get(ADDRESS, 0)
                inputTensors += tensor
                inputBuffers += buffer
                api.check(api.setInputTensor.invokeExact(request, index.toLong(), tensor) as Int, "set input $index")
            }
        }
    }

    /** Copies [data] into input [index]. It must have exactly the size of the shape given to [bindInputs]. */
    fun setInput(index: Int, data: FloatArray) {
        check(!isClosed) { "model already closed" }
        val buffer = inputBuffers[index]
        require(data.size * FLOAT_BYTES == buffer.byteSize()) { "input $index takes ${buffer.byteSize() / FLOAT_BYTES} floats, not ${data.size}" }
        MemorySegment.copy(MemorySegment.ofArray(data), 0, buffer, 0, buffer.byteSize())
    }

    /** Copies [floatCount] floats from [source] (native memory the caller owns) into input [index]. */
    fun setInput(index: Int, source: MemorySegment, floatCount: Long) {
        check(!isClosed) { "model already closed" }
        val buffer = inputBuffers[index]
        require(floatCount * FLOAT_BYTES == buffer.byteSize()) { "input $index takes ${buffer.byteSize() / FLOAT_BYTES} floats, not $floatCount" }
        MemorySegment.copy(source, 0, buffer, 0, buffer.byteSize())
    }

    /** Runs the model on the current inputs. Blocking: about a second for one Demucs segment. */
    fun infer() {
        check(!isClosed) { "model already closed" }
        api.check(api.infer.invokeExact(request) as Int, "run the model")
    }

    /** Number of floats of output [index] after an [infer]. */
    fun outputSize(index: Int): Long = withOutput(index) { _, bytes -> bytes / FLOAT_BYTES }

    /** Copies output [index] into [into], which must have exactly its size. */
    fun copyOutput(index: Int, into: FloatArray) {
        withOutput(index) { data, bytes ->
            require(bytes == into.size.toLong() * FLOAT_BYTES) { "output $index has ${bytes / FLOAT_BYTES} floats, not ${into.size}" }
            MemorySegment.copy(data.reinterpret(bytes), 0, MemorySegment.ofArray(into), 0, bytes)
        }
    }

    // The request hands out a new handle at each call: free it as soon as the data has been read.
    private fun <T> withOutput(index: Int, read: (data: MemorySegment, bytes: Long) -> T): T {
        check(!isClosed) { "model already closed" }
        Arena.ofConfined().use { arena ->
            val out = arena.allocate(ADDRESS)
            api.check(api.getOutputTensor.invokeExact(request, index.toLong(), out) as Int, "get output $index")
            val tensor = out.get(ADDRESS, 0)
            try {
                api.check(api.tensorData.invokeExact(tensor, out) as Int, "read output $index")
                val data = out.get(ADDRESS, 0)
                api.check(api.tensorByteSize.invokeExact(tensor, out) as Int, "size output $index")
                return read(data, out.get(JAVA_LONG, 0))
            } finally {
                api.tensorFree.invokeExact(tensor)
            }
        }
    }

    @Synchronized
    override fun close() {
        if (isClosed) return
        isClosed = true
        inputTensors.forEach { api.tensorFree.invokeExact(it) }
        api.inferRequestFree.invokeExact(request)
        api.compiledModelFree.invokeExact(compiled)
        api.modelFree.invokeExact(model)
        buffers.close()
        onClose(this)
    }

    internal companion object {
        private const val ELEMENT_TYPE_F32 = 4 // ov_element_type_e: DYNAMIC 0, BOOLEAN 1, BF16 2, F16 3, F32 4
        private const val FLOAT_BYTES = 4L

        fun create(api: OpenVinoRuntime.Api, core: MemorySegment, model: MemorySegment, onClose: (OpenVinoModel) -> Unit): OpenVinoModel =
            Arena.ofConfined().use { arena ->
                val out = arena.allocate(ADDRESS)
                api.check(
                    api.compileModel.invokeExact(
                        core, model, arena.allocateFrom("CPU"), 4L, out,
                        arena.allocateFrom("PERFORMANCE_HINT"), arena.allocateFrom("LATENCY"),
                        arena.allocateFrom(PRECISION_KEY), arena.allocateFrom("f32"),
                    ) as Int,
                    "compile the model",
                )
                val compiled = out.get(ADDRESS, 0)
                try {
                    val precision = readProperty(api, compiled, PRECISION_KEY, arena)
                    if (precision != "f32") throw OpenVinoException("the CPU kept the inference precision $precision, not f32")
                    api.check(api.createInferRequest.invokeExact(compiled, out) as Int, "create the infer request")
                    OpenVinoModel(api, model, compiled, out.get(ADDRESS, 0), onClose)
                } catch (e: Throwable) {
                    api.compiledModelFree.invokeExact(compiled)
                    throw e
                }
            }

        private const val PRECISION_KEY = "INFERENCE_PRECISION_HINT"

        private fun readProperty(api: OpenVinoRuntime.Api, compiled: MemorySegment, key: String, arena: Arena): String {
            val out = arena.allocate(ADDRESS)
            api.check(api.compiledModelGetProperty.invokeExact(compiled, arena.allocateFrom(key), out) as Int, "read $key")
            val text = out.get(ADDRESS, 0)
            // OpenVINO allocated the string: it frees it too.
            return try {
                text.reinterpret(Long.MAX_VALUE).getString(0)
            } finally {
                api.free.invokeExact(text)
            }
        }
    }
}
