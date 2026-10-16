# The engine's C++ tests (engine\tests), in the build folder Gradle configured for the engine.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @(':engine-desktop:configureNativeEngine')
$buildDir = Join-Path $RepoRoot 'engine-desktop\build\native'
Invoke-Native cmake @('--build', $buildDir, '--config', 'Release', '--target', 'sdengine_tests', '-j', "$env:NUMBER_OF_PROCESSORS")
Invoke-Native (Join-Path $buildDir 'sdengine_tests.exe')
