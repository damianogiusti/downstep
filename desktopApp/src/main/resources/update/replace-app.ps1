# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new folder in
# place of the old one, and starts the app again. The old folder goes to the Recycle Bin: nothing is
# deleted for good. Paths go through -LiteralPath: [ and ] are not wildcards.
# $TrashDir stands in for the Recycle Bin in tests.
param([int]$ProcessId, [string]$NewApp, [string]$Target, [string]$TrashDir = "")

$exe = Join-Path $Target "Downstep.exe"
$name = Split-Path $Target -Leaf
$oldName = "$name (old)"
$old = Join-Path (Split-Path $Target -Parent) $oldName

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
    exit 1
}

# Left by an update that stopped halfway.
try {
    Move-ToTrash $old
} catch {
    Write-Output "Cannot move $old to the Recycle Bin: nothing changed. $_"
    Start-Process -FilePath $exe -WorkingDirectory $Target
    exit 1
}
# Antivirus or the search indexer can hold a file for a moment after the app quits: try again.
$moved = $false
for ($i = 0; $i -lt 40 -and -not $moved; $i++) {
    try {
        Rename-Item -LiteralPath $Target -NewName $oldName -ErrorAction Stop
        $moved = $true
    } catch {
        Start-Sleep -Milliseconds 500
    }
}
if (-not $moved) {
    Write-Output "Cannot move the installed app: nothing changed"
    Start-Process -FilePath $exe -WorkingDirectory $Target
    exit 1
}

$replaced = $false
try {
    Rename-Item -LiteralPath $NewApp -NewName $name -ErrorAction Stop
    $replaced = $true
    Write-Output "Replaced"
} catch {
    Write-Output "Cannot move the new app: the old one goes back. $_"
    Rename-Item -LiteralPath $old -NewName $name -ErrorAction Stop
}
$app = Start-Process -FilePath $exe -WorkingDirectory $Target -PassThru
Write-Output "Started $($app.Id)"
if ($replaced) {
    try {
        Move-ToTrash $old
        Write-Output "Old app in the Recycle Bin"
    } catch {
        Write-Output "The old app stays as $old. $_"
    }
}
