# Removes what the build made: Gradle's outputs (the engine's CMake folder too) and build\ at the
# root (packages and what the container copied back). The Docker volumes stay: make docker-clean.
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain
Invoke-Gradle @('clean')
$rootBuild = Join-Path $RepoRoot 'build'
if (Test-Path $rootBuild) { Remove-Item -Recurse -Force $rootBuild }
