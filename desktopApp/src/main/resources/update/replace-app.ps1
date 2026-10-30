# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new folder in
# place of the old one, and starts the app again. The old folder goes to the Recycle Bin: nothing is
# deleted for good. Paths go through -LiteralPath: [ and ] are not wildcards.
# $TrashDir stands in for the Recycle Bin in tests.
# Next to this script it leaves swap-result, one word the next start reports, and it holds update.lock
# while it runs (see UpdateSwapFiles in the app).
param([int]$ProcessId, [string]$NewApp, [string]$Target, [string]$TrashDir = "")

$exe = Join-Path $Target "Downstep.exe"
$name = Split-Path $Target -Leaf
$oldName = "$name (old)"
$old = Join-Path (Split-Path $Target -Parent) $oldName
$resultFile = Join-Path $PSScriptRoot "swap-result"
$lockFile = Join-Path $PSScriptRoot "update.lock"

# Windows does not rename a folder that is any process's current directory: never work from the app's.
[Environment]::CurrentDirectory = $PSScriptRoot
Set-Location -LiteralPath $PSScriptRoot

# A copy of Downstep opened by hand now would lock the files this script moves. It finds this lock
# taken and quits at once. Windows releases the lock when the script ends, however it ends, and the
# app ignores a lock older than a few minutes: an update can never keep the app closed.
$lock = $null
try {
    $lock = [System.IO.File]::Open($lockFile, 'OpenOrCreate', 'ReadWrite', 'ReadWrite')
    $lock.Lock(0, 1)
    # Written once locked, through the same handle: the file's date tells the app how old the lock is.
    $stamp = [System.Text.Encoding]::ASCII.GetBytes((Get-Date -Format o))
    $lock.SetLength(0)
    $lock.Write($stamp, 0, $stamp.Length)
    $lock.Flush()
    Write-Output "Holding the update lock"
} catch {
    Write-Output "No update lock: $_"
    if ($lock) { $lock.Dispose() }
    $lock = $null
}

function Write-Result([string]$outcome) {
    Set-Content -LiteralPath $resultFile -Value $outcome -Encoding ascii -ErrorAction SilentlyContinue
}

# The lock goes first, explicitly: the app started here must not find it and quit. If the old folder
# could not be renamed back, the app starts from there: some app always opens.
function Start-App {
    if ($script:lock) {
        try { $script:lock.Unlock(0, 1) } catch { }
        $script:lock.Dispose()
        $script:lock = $null
    }
    $folder = $Target
    if (-not (Test-Path -LiteralPath $exe)) { $folder = $old }
    Start-Process -FilePath (Join-Path $folder "Downstep.exe") -WorkingDirectory $folder -PassThru
}

# Moves the whole folder at once, as Explorer's Delete does: nothing inside it is opened or followed.
# An exe that just quit can stay locked for a moment, so it tries again.
function Move-ToTrash([string]$path) {
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Test-Path -LiteralPath $path)) { return }
        try {
            if ($TrashDir) {
                Move-Item -LiteralPath $path -Destination $TrashDir -ErrorAction Stop
            } else {
                Add-Type -AssemblyName Microsoft.VisualBasic
                [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory(
                    $path, [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,
                    [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
            }
            return
        } catch {
            if ($i -eq 19) { throw }
            Start-Sleep -Milliseconds 500
        }
    }
}

Write-Output "$(Get-Date): waiting for $ProcessId"
Wait-Process -Id $ProcessId -Timeout 120 -ErrorAction SilentlyContinue
if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
    Write-Output "The app did not quit: nothing changed"
    Write-Result "app-did-not-quit"
    exit 1
}

# Left by an update that stopped halfway.
try {
    Move-ToTrash $old
} catch {
    Write-Output "Cannot move $old to the Recycle Bin: nothing changed. $_"
    Write-Result "old-copy-stuck"
    Start-App | Out-Null
    exit 1
}
# Antivirus or the search indexer can hold a file for a moment after the app quits, and a copy opened
# by hand needs a moment to see the lock and quit: try again, for up to 30 seconds.
$moved = $false
for ($i = 0; $i -lt 60 -and -not $moved; $i++) {
    try {
        Rename-Item -LiteralPath $Target -NewName $oldName -ErrorAction Stop
        $moved = $true
    } catch {
        Start-Sleep -Milliseconds 500
    }
}
if (-not $moved) {
    Write-Output "Cannot move the installed app: nothing changed"
    Write-Result "installed-app-in-use"
    Start-App | Out-Null
    exit 1
}

$replaced = $false
try {
    Rename-Item -LiteralPath $NewApp -NewName $name -ErrorAction Stop
    $replaced = $true
    Write-Output "Replaced"
    Write-Result "replaced"
} catch {
    Write-Output "Cannot move the new app: the old one goes back. $_"
    Write-Result "new-app-not-moved"
    # Without this rename there is no app at all: try as long as for the first one.
    for ($i = 0; $i -lt 60; $i++) {
        try {
            Rename-Item -LiteralPath $old -NewName $name -ErrorAction Stop
            break
        } catch {
            if ($i -eq 59) { Write-Output "The old app stays as $old. $_" }
            Start-Sleep -Milliseconds 500
        }
    }
}
$app = Start-App
Write-Output "Started $($app.Id)"
if ($replaced) {
    try {
        Move-ToTrash $old
        Write-Output "Old app in the Recycle Bin"
    } catch {
        Write-Output "The old app stays as $old. $_"
    }
}
