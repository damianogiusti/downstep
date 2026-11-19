// The OpenVINO runtime (Apache-2.0) that separates a track into stems. It is not built here: the
// official PyPI wheel of the build machine's platform is downloaded, checked against the hash below
// and stripped to the five libraries the app needs (the C API, the core, the IR reader, the CPU plugin
// and oneTBB). Url and SHA-256 come from https://pypi.org/pypi/openvino/<version>/json.
//
// OpenVINO has no macOS x86_64 build after 2025.4.1, so Intel Macs run that version.
// This file is also the key of the wheel cache in the CI workflows: change a pin here, and CI
// downloads again.

data class OpenVinoWheel(
    val file: String,
    val url: String,
    val sha256: String,
    val distInfo: String,
    // Names inside openvino/libs/ of the wheel; the first one is the C API, which the binding opens.
    val libraries: List<String>,
)

val osNameForOpenVino: String = System.getProperty("os.name")
val archForOpenVino: String = System.getProperty("os.arch")

val openVinoWheel: OpenVinoWheel? = when {
    osNameForOpenVino.startsWith("Mac") && (archForOpenVino == "aarch64" || archForOpenVino == "arm64") -> OpenVinoWheel(
        file = "openvino-2026.4.1-22982-cp312-cp312-macosx_11_0_arm64.whl",
        url = "https://files.pythonhosted.org/packages/b3/4e/865889882a3be23beaf9808f93069c05e2eb8c8ff4e9b913568fc0383ce4/openvino-2026.4.1-22982-cp312-cp312-macosx_11_0_arm64.whl",
        sha256 = "726ac547b8474a5e7b145bc1ae5a8bb6fbcbb60b79bd9a611c67eec2c74b7a5f",
        distInfo = "openvino-2026.4.1.dist-info",
        libraries = listOf(
            "libopenvino_c.2641.dylib", "libopenvino.2641.dylib", "libopenvino_ir_frontend.2641.dylib",
            "libopenvino_arm_cpu_plugin.so", "libtbb.12.dylib",
        ),
    )
    osNameForOpenVino.startsWith("Mac") && (archForOpenVino == "x86_64" || archForOpenVino == "amd64") -> OpenVinoWheel(
        file = "openvino-2025.4.1-20426-cp312-cp312-macosx_10_15_x86_64.whl",
        url = "https://files.pythonhosted.org/packages/a8/9f/77da5039032063747dcf39434662f31d914fd515de86a20a405eea315c53/openvino-2025.4.1-20426-cp312-cp312-macosx_10_15_x86_64.whl",
        sha256 = "8994fcc9082fe4aed1ed2a6e531280de38d7293943c00284560b854945d5af9b",
        distInfo = "openvino-2025.4.1.dist-info",
        libraries = listOf(
            "libopenvino_c.2541.dylib", "libopenvino.2541.dylib", "libopenvino_ir_frontend.2541.dylib",
            "libopenvino_intel_cpu_plugin.so", "libtbb.12.dylib",
        ),
    )
    osNameForOpenVino.startsWith("Windows") -> OpenVinoWheel(
        file = "openvino-2026.4.1-22982-cp312-cp312-win_amd64.whl",
        url = "https://files.pythonhosted.org/packages/d0/44/66a61b7cfccea1dfa20e95a04b4157f07a0e4dc3f7e894b22a92abb8822b/openvino-2026.4.1-22982-cp312-cp312-win_amd64.whl",
        sha256 = "4e04316abff1b99e29b8cbd38deaef9bde4739eba216d982d4b3981e456ecd87",
        distInfo = "openvino-2026.4.1.dist-info",
        libraries = listOf(
            "openvino_c.dll", "openvino.dll", "openvino_ir_frontend.dll", "openvino_intel_cpu_plugin.dll", "tbb12.dll",
        ),
    )
    else -> null
}

// Notices the wheel carries for what it contains. The app ships them in its licenses folder, flat
// (the Windows zip copies that folder without recursion), under these names.
val openVinoNotices = mapOf(
    "licensing/runtime-third-party-programs.txt" to "OpenVINO-runtime-third-party-programs.txt",
    "licensing/onetbb_third-party-programs.txt" to "OpenVINO-oneTBB-third-party-programs.txt",
    "licensing/onednn_third-party-programs.txt" to "OpenVINO-oneDNN-third-party-programs.txt",
)

// The wheel stays here between builds, outside build/, so `clean` does not download it again. CI caches this folder.
val openVinoWheelCache = rootProject.layout.projectDirectory.dir(".openvino-cache")
val openVinoRuntimeDir = layout.buildDirectory.dir("openvino/runtime")
val openVinoNoticesDir = layout.buildDirectory.dir("openvino/licenses")

val fetchOpenVinoRuntime = tasks.register("fetchOpenVinoRuntime") {
    group = "build"
    description = "Downloads the pinned OpenVINO wheel and extracts the libraries the app ships."
    val wheel = openVinoWheel
    enabled = wheel != null
    if (wheel != null) {
        inputs.property("wheel", "${wheel.file} ${wheel.sha256} ${wheel.libraries}")
        inputs.property("notices", openVinoNotices.toString())
    }
    outputs.dir(openVinoRuntimeDir)
    outputs.dir(openVinoNoticesDir)
    doLast {
        wheel ?: return@doLast
        fun sha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val cacheDir = openVinoWheelCache.asFile.also { it.mkdirs() }
        val cached = File(cacheDir, wheel.file)
        // A cached file with another hash (cut off download, old pin) is thrown away, never trusted.
        if (cached.exists() && sha256(cached) != wheel.sha256) cached.delete()
        if (!cached.exists()) {
            val partial = File(cacheDir, wheel.file + ".part")
            logger.lifecycle("Downloading ${wheel.file}")
            java.net.URI(wheel.url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            val actual = sha256(partial)
            if (actual != wheel.sha256) {
                partial.delete()
                throw GradleException("${wheel.file}: SHA-256 is $actual, expected ${wheel.sha256}")
            }
            partial.renameTo(cached)
        }

        val runtime = openVinoRuntimeDir.get().asFile
        val notices = openVinoNoticesDir.get().asFile
        runtime.deleteRecursively(); notices.deleteRecursively()
        runtime.mkdirs(); notices.mkdirs()
        java.util.zip.ZipFile(cached).use { zip ->
            fun extract(entryName: String, target: File) {
                val entry = zip.getEntry(entryName) ?: throw GradleException("${wheel.file} has no $entryName")
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            wheel.libraries.forEach { extract("openvino/libs/$it", File(runtime, it)) }
            openVinoNotices.forEach { (inWheel, name) ->
                extract("${wheel.distInfo}/licenses/$inWheel", File(notices, name))
            }
        }
        if (osNameForOpenVino.startsWith("Mac")) {
            wheel.libraries.map { File(runtime, it) }.filter(::trimSignaturePadding).forEach { library ->
                // The trimmed header no longer matches the old signature: sign again, ad hoc, as jpackage
                // does for the rest of the app.
                val signed = ProcessBuilder("codesign", "--force", "--sign", "-", library.path).inheritIO().start().waitFor()
                if (signed != 0) throw GradleException("codesign failed on ${library.name}")
            }
        }
    }
}

/**
 * The wheel's libtbb ends its __LINKEDIT segment with 16 zero bytes after the code signature. codesign
 * refuses such a file ("internal error in Code Signing subsystem"), and jpackage runs
 * `codesign --remove-signature` on every library it bundles. This shrinks the segment to end with the
 * signature and drops the padding. Returns true if the file was changed; any other layout is left alone.
 */
fun trimSignaturePadding(file: File): Boolean {
    val data = file.readBytes()
    val buffer = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    if (data.size < 32 || buffer.getInt(0) != 0xFEEDFACF.toInt()) return false
    val commandCount = buffer.getInt(16)
    var offset = 32
    var linkEdit = -1
    var signature = -1
    repeat(commandCount) {
        val command = buffer.getInt(offset)
        if (command == 0x19 && String(data, offset + 8, 16).trimEnd('\u0000') == "__LINKEDIT") linkEdit = offset
        if (command == 0x1D) signature = offset
        offset += buffer.getInt(offset + 4)
    }
    if (linkEdit < 0 || signature < 0) return false
    val segmentStart = buffer.getLong(linkEdit + 40)
    val segmentSize = buffer.getLong(linkEdit + 48)
    val signatureEnd = buffer.getInt(signature + 8).toLong() + buffer.getInt(signature + 12).toLong()
    val isPaddedAfterSignature = segmentStart + segmentSize == data.size.toLong() &&
        signatureEnd < data.size && (signatureEnd.toInt() until data.size).all { data[it] == 0.toByte() }
    if (!isPaddedAfterSignature) return false
    buffer.putLong(linkEdit + 48, signatureEnd - segmentStart)
    file.writeBytes(data.copyOf(signatureEnd.toInt()))
    return true
}
