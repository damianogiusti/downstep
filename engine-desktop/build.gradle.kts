plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    // FFM (java.lang.foreign) is final from Java 22.
    jvmToolchain(23)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":shared-data"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// The native engine (engine/, CMake). Built here so tests and the desktop app always load a
// library that matches the sources. macOS (AudioToolbox decoder) and Windows (Media Foundation).
val engineDir = rootProject.layout.projectDirectory.dir("engine")
val engineBuildDir = layout.buildDirectory.dir("native")
// libsdengine.dylib on macOS, sdengine.dll on Windows.
val engineLibrary = engineBuildDir.map { it.file(System.mapLibraryName("sdengine")) }
val cmake = providers.gradleProperty("cmake.path").orElse("cmake")
val osName = System.getProperty("os.name")
val hasEngine = osName.startsWith("Mac") || osName.startsWith("Windows")

val configureNativeEngine = tasks.register<Exec>("configureNativeEngine") {
    inputs.file(engineDir.file("CMakeLists.txt"))
    outputs.file(engineBuildDir.map { it.file("CMakeCache.txt") })
    commandLine(cmake.get(), "-S", engineDir.asFile.path, "-B", engineBuildDir.get().asFile.path, "-DCMAKE_BUILD_TYPE=Release")
}

val buildNativeEngine = tasks.register<Exec>("buildNativeEngine") {
    dependsOn(configureNativeEngine)
    inputs.dir(engineDir.dir("src"))
    inputs.dir(engineDir.dir("include"))
    inputs.file(engineDir.file("CMakeLists.txt"))
    outputs.file(engineLibrary)
    val jobs = Runtime.getRuntime().availableProcessors().toString()
    // --config: Visual Studio builds every configuration from one build dir; ignored elsewhere.
    commandLine(cmake.get(), "--build", engineBuildDir.get().asFile.path, "--config", "Release", "--target", "sdengine", "-j", jobs)
}

tasks.test {
    // No decoder for other systems yet: there `check` stays green.
    enabled = hasEngine
    dependsOn(buildNativeEngine)
    systemProperty("slowdowner.engine.library", engineLibrary.get().asFile.path)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
