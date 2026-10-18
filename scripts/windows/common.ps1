# Shared by the Windows scripts (dot-sourced). PowerShell 7 or Windows PowerShell 5.1, nothing else:
# no bash, Git Bash or WSL. The engine builds as in release.yml: MinGW-w64 GCC, CMake and Ninja.

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

# Runs a native command and stops when it fails: $ErrorActionPreference does not cover exit codes.
function Invoke-Native {
    param([Parameter(Mandatory)][string] $Command, [string[]] $Arguments = @())
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command exited with code $LASTEXITCODE"
    }
}

# Puts the first folder that has the tool in front of PATH, when PATH does not have it yet.
function Add-ToolToPath {
    param([Parameter(Mandatory)][string] $Exe, [string[]] $Candidates)
    if (Get-Command $Exe -ErrorAction SilentlyContinue) { return }
    foreach ($dir in $Candidates) {
        if ($dir -and (Test-Path (Join-Path $dir $Exe))) {
            $env:PATH = "$dir;$env:PATH"
            return
        }
    }
}

# Finds JDK 23, CMake, Ninja and MinGW: PATH first, then the usual install folders (MSYS2 as in
# release.yml, the CMake installer, winget, Chocolatey). JAVA_HOME, CMAKE_GENERATOR, CC and CXX are
# kept when already set, so another toolchain stays one variable away.
function Initialize-Toolchain {
    param([switch] $Engine)

    if ($env:JAVA_HOME) {
        $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
    }
    if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
        throw 'JDK 23 not found: install it and set JAVA_HOME.'
    }
    if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path (Join-Path $RepoRoot 'local.properties'))) {
        $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path $default) {
            $env:ANDROID_HOME = $default
        } else {
            throw 'Android SDK not found: set ANDROID_HOME (Gradle needs it to configure the shared modules).'
        }
    }
    if (-not $Engine) { return }

    # CMake's FetchContent checks out Bungee and its submodules (Eigen) with git, and a deep worktree
    # folder goes past MAX_PATH ('Filename too long'). Turn on core.longpaths for the git processes
    # started from here, unless the user has it already, after any GIT_CONFIG_* entries already set.
    $longPaths = if (Get-Command git.exe -ErrorAction SilentlyContinue) { & git.exe config --get core.longpaths }
    if ($longPaths -ne 'true') {
        $n = if ($env:GIT_CONFIG_COUNT) { [int]$env:GIT_CONFIG_COUNT } else { 0 }
        Set-Item "env:GIT_CONFIG_KEY_$n" 'core.longpaths'
        Set-Item "env:GIT_CONFIG_VALUE_$n" 'true'
        $env:GIT_CONFIG_COUNT = "$($n + 1)"
    }

    $msys = if ($env:MSYS2_ROOT) { $env:MSYS2_ROOT } else { 'C:\msys64' }
    $winget = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $wingetNinja = if (Test-Path $winget) {
        Get-ChildItem $winget -Directory -Filter 'Ninja-build.Ninja*' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    }
    Add-ToolToPath 'gcc.exe' @("$msys\ucrt64\bin", "$msys\mingw64\bin", 'C:\ProgramData\mingw64\mingw64\bin', 'C:\mingw64\bin')
    Add-ToolToPath 'cmake.exe' @("$env:ProgramFiles\CMake\bin", "$msys\ucrt64\bin")
    Add-ToolToPath 'ninja.exe' @("$msys\ucrt64\bin", $wingetNinja, "$env:ProgramData\chocolatey\bin")
    foreach ($tool in 'gcc.exe', 'g++.exe', 'cmake.exe', 'ninja.exe') {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            throw "$tool not found. The engine needs MinGW-w64 GCC, CMake 3.30+ and Ninja on PATH (see README, Build)."
        }
    }
    # Bungee builds for Windows with MinGW-w64, not MSVC.
    if (-not $env:CMAKE_GENERATOR) { $env:CMAKE_GENERATOR = 'Ninja' }
    if (-not $env:CC) { $env:CC = 'gcc' }
    if (-not $env:CXX) { $env:CXX = 'g++' }
}

# Gradle from the repository root, with -PappVersion when APP_VERSION is set and -PreleaseRepo when
# RELEASE_REPO is set (as in release.yml).
function Invoke-Gradle {
    param([Parameter(Mandatory)][string[]] $Tasks)
    $arguments = @($Tasks)
    if ($env:APP_VERSION) { $arguments += "-PappVersion=$env:APP_VERSION" }
    if ($env:RELEASE_REPO) { $arguments += "-PreleaseRepo=$env:RELEASE_REPO" }
    Push-Location $RepoRoot
    try {
        Invoke-Native (Join-Path $RepoRoot 'gradlew.bat') $arguments
    } finally {
        Pop-Location
    }
}
