# The app folder, zipped as in release.yml: build\dist\Downstep-windows-x64.zip, with the licenses
# also at the top of the zip. APP_VERSION sets the version (1.0.0 otherwise).
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Toolchain -Engine
Invoke-Gradle @(':desktopApp:createDistributable')

$dist = Join-Path $RepoRoot 'build\dist'
$stage = Join-Path $dist 'zip'
$zip = Join-Path $dist 'Downstep-windows-x64.zip'
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
if (Test-Path $zip) { Remove-Item -Force $zip }
New-Item -ItemType Directory -Force (Join-Path $stage 'licenses') | Out-Null
Copy-Item -Recurse (Join-Path $RepoRoot 'desktopApp\build\compose\binaries\main\app\Downstep') (Join-Path $stage 'Downstep')
Copy-Item (Join-Path $stage 'Downstep\app\resources\licenses\*') (Join-Path $stage 'licenses')
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zip
Remove-Item -Recurse -Force $stage
Write-Host "Package: $zip"
