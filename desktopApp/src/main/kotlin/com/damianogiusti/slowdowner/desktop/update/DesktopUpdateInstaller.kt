package com.damianogiusti.slowdowner.desktop.update

import com.damianogiusti.slowdowner.data.update.UpdateInstaller
import com.damianogiusti.slowdowner.logging.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The app downloads its update itself, and that is what keeps macOS and Windows quiet: a browser
 * marks downloaded files (quarantine on macOS, "Mark of the Web" on Windows), and a marked app asks
 * for approval again. A file written by the app carries no mark, so the new version opens like the
 * old one did.
 *
 * The swap happens in a small script after the app quits: a running app cannot replace its own
 * files safely (Windows locks them, macOS caches code signatures by file).
 */
class DesktopUpdateInstaller(
    private val installation: AppInstallation?,
    private val workDir: File,
    private val logger: Logger,
    private val processId: Long = ProcessHandle.current().pid(),
) : UpdateInstaller {

    override val assetName: String? = when (installation) {
        is AppInstallation.MacBundle -> MAC_ASSET
        is AppInstallation.WindowsFolder -> WINDOWS_ASSET
        null -> null
    }

    override fun prepare(download: File, version: String) {
        workDir.mkdirs()
        when (installation) {
            is AppInstallation.MacBundle -> prepareMac(installation.bundle, download, version)
            is AppInstallation.WindowsFolder -> prepareWindows(installation.folder, download, version)
            null -> error("This app cannot replace itself")
        }
    }

    private fun prepareMac(bundle: File, dmg: File, version: String) {
        val staged = File(workDir, bundle.name)
        staged.deleteTree()
        DiskImage.copyApp(dmg, staged, logger)
        checkApp(File(staged, "Contents/app"), version)
        // Checks the whole bundle against its signature: a damaged copy would not open.
        run("codesign", "--verify", "--deep", "--strict", staged.path)
        val script = script("replace-app.sh")
        start(listOf("/bin/sh", script.path, processId.toString(), staged.path, bundle.path))
    }

    private fun prepareWindows(folder: File, zip: File, version: String) {
        // Next to the installed folder: on the same disk, the swap is two renames.
        val staged = File(folder.parentFile, folder.name + ".update")
        staged.deleteTree()
        // The zip has a Downstep folder, whatever name the user gave to theirs.
        ZipArchive.extractFolder(zip, APP_NAME, staged)
        checkApp(File(staged, "app"), version)
        if (!File(staged, "$APP_NAME.exe").isFile) throw IOException("No $APP_NAME.exe in the update")
        val script = script("replace-app.ps1")
        start(
            listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-File", script.path, "-ProcessId", processId.toString(), "-NewApp", staged.path, "-Target", folder.path,
            ),
        )
    }

    /** The launcher settings name our main class and the expected version: the right app, the right release. */
    private fun checkApp(appDir: File, version: String) {
        val config = appDir.listFiles { file -> file.extension == "cfg" }?.firstOrNull()
            ?: throw IOException("No launcher settings in the update")
        val lines = config.readLines()
        if ("app.mainclass=$MAIN_CLASS" !in lines) throw IOException("The update is not Downstep")
        if (lines.none { it == "java-options=-D$VERSION_PROPERTY=$version" }) throw IOException("The update is not version $version")
    }

    private fun script(name: String): File {
        val target = File(workDir, name)
        val source = javaClass.getResourceAsStream("/update/$name") ?: error("Missing script $name")
        source.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /** Runs on after the app quits: nothing here waits for it. Its output goes to a log next to it. */
    private fun start(command: List<String>) {
        val log = File(workDir, "replace-app.log")
        ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
            .start()
    }

    private fun nullDevice() = File(if (File.separatorChar == '\\') "NUL" else "/dev/null")

    companion object {
        const val APP_NAME = "Downstep"
        const val MAC_ASSET = "Downstep-mac.dmg"
        const val WINDOWS_ASSET = "Downstep-windows-x64.zip"
        const val VERSION_PROPERTY = "slowdowner.version"
        private const val MAIN_CLASS = "com.damianogiusti.slowdowner.desktop.MainKt"

        /** Runs a command to the end; throws with its output if it fails or hangs. */
        internal fun run(vararg command: String): String {
            // Output to a file, not a pipe: reading a pipe would wait for the end and skip the timeout.
            val output = File.createTempFile("downstep-command", ".txt")
            try {
                val process = ProcessBuilder(*command).redirectErrorStream(true).redirectOutput(output).start()
                if (!process.waitFor(COMMAND_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                    process.destroyForcibly()
                    throw IOException("${command.first()} did not finish")
                }
                val text = output.readText()
                if (process.exitValue() != 0) throw IOException("${command.first()} failed (${process.exitValue()}): ${text.trim()}")
                return text
            } finally {
                output.delete()
            }
        }

        private const val COMMAND_TIMEOUT_MINUTES = 5L
    }
}
