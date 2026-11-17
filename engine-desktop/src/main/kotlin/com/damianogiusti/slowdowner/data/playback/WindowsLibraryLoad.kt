package com.damianogiusti.slowdowner.data.playback

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.nio.charset.StandardCharsets.UTF_16LE
import java.nio.file.Path

/**
 * Windows refused to load the engine's code by policy (Smart App Control, App Control for Business,
 * AppLocker), not because a file is missing or broken. [cause] is the load error.
 */
class EngineLibraryBlockedException(cause: Throwable) : Exception("Windows blocked the engine library by policy", cause)

/**
 * The Windows error code of loading a DLL. Java's UnsatisfiedLinkError only carries the system's text,
 * in the language of Windows: the code tells a security policy block from a broken or missing file.
 */
internal object WindowsLibraryLoad {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    // ERROR_SYSTEM_INTEGRITY_POLICY_VIOLATION: App Control for Business, and Smart App Control built on it.
    private const val APPLICATION_CONTROL = 4551

    // ERROR_ACCESS_DISABLED_BY_POLICY: AppLocker and software restriction policies.
    private const val GROUP_POLICY = 1260

    fun isBlockedByPolicy(errorCode: Int?): Boolean = errorCode == APPLICATION_CONTROL || errorCode == GROUP_POLICY

    /**
     * [error] wrapped in [EngineLibraryBlockedException] when loading [library] once more fails by
     * policy, else [error] itself. Only after a failure: it loads the DLL again just to read the code.
     */
    fun explain(library: Path, error: Throwable): Throwable =
        if (isBlockedByPolicy(runCatching { errorCode(library) }.getOrNull())) EngineLibraryBlockedException(error) else error

    /** GetLastError of LoadLibraryW on [library]; null when it loads or off Windows. */
    fun errorCode(library: Path): Int? {
        if (!isWindows) return null
        val linker = Linker.nativeLinker()
        val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
        val stateLayout = Linker.Option.captureStateLayout()
        val lastError = stateLayout.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"))
        val loadLibrary = linker.downcallHandle(
            kernel32.find("LoadLibraryW").orElseThrow(),
            FunctionDescriptor.of(ADDRESS, ADDRESS),
            Linker.Option.captureCallState("GetLastError"),
        )
        val freeLibrary = linker.downcallHandle(kernel32.find("FreeLibrary").orElseThrow(), FunctionDescriptor.of(JAVA_INT, ADDRESS))
        return Arena.ofConfined().use { arena ->
            val state = arena.allocate(stateLayout)
            val name = arena.allocateFrom(library.toAbsolutePath().toString(), UTF_16LE)
            val module = loadLibrary.invokeExact(state, name) as MemorySegment
            if (module != MemorySegment.NULL) {
                freeLibrary.invokeExact(module) as Int
                null
            } else {
                lastError.get(state, 0L) as Int
            }
        }
    }
}
