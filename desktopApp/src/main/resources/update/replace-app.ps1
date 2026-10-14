# Started by Downstep before it quits to update itself. Waits for it to quit, puts the new folder in
# place of the old one, and starts the app again. Paths go through -LiteralPath: [ and ] are not wildcards.
param([int]$ProcessId, [string]$NewApp, [string]$Target)

$exe = Join-Path $Target "Downstep.exe"
$name = Split-Path $Target -Leaf
$backupName = "$name.old"
$backup = Join-Path (Split-Path $Target -Parent) $backupName

# Unlike Remove-Item -Recurse in Windows PowerShell 5.1, this never follows a junction out of the folder.
function Remove-Folder([string]$path) {
    if (Test-Path -LiteralPath $path) { [System.IO.Directory]::Delete($path, $true) }
}

Write-Output "$(Get-Date): waiting for $ProcessId"
Wait-Process -Id $ProcessId -Timeout 120 -ErrorAction SilentlyContinue
if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
    Write-Output "The app did not quit: nothing changed"
    exit 1
}

try { Remove-Folder $backup } catch { Write-Output "Cannot remove an old backup: $_" }
# Antivirus or the search indexer can hold a file for a moment after the app quits: try again.
$moved = $false
for ($i = 0; $i -lt 40 -and -not $moved; $i++) {
    try {
        Rename-Item -LiteralPath $Target -NewName $backupName -ErrorAction Stop
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
    Rename-Item -LiteralPath $backup -NewName $name -ErrorAction Stop
}
$app = Start-Process -FilePath $exe -WorkingDirectory $Target -PassThru
Write-Output "Started $($app.Id)"
if ($replaced) {
    try { Remove-Folder $backup } catch { Write-Output "Cannot remove the old app: $_" }
}
