# Compiles the desktop app and everything it uses, with the native engine.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @(':engine-desktop:buildNativeEngine', ':desktopApp:jar')
