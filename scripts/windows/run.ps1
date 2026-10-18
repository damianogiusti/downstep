# Starts the app from the sources.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @(':desktopApp:run')
