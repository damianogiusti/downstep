# The Kotlin tests of every module, the same set as the README. The desktop ones load the engine.
# --continue: every module runs, also after a failure.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @('--continue', ':shared:jvmTest', ':shared-data:desktopTest', ':shared-ui:desktopTest', ':engine-desktop:test', ':desktopApp:test')

