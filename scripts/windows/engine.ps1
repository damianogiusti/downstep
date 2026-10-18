# The native engine, sdengine.dll and mp3lame.dll in engine-desktop\build\native. Gradle drives
# CMake, so this is the same build the tests and the app use.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @(':engine-desktop:buildNativeEngine')
