# The Docker targets: docker.ps1 <image|build|test|engine|engine-test|package|shell|clean>.
# Builds the image (quick when nothing changed), then runs the target in a new container with the
# repository mounted read-only; see docker/container-run.sh. What the target produces lands in
# build\docker-out. Two volumes keep Gradle's caches (shared) and the container's build (one per
# checkout, named after its path) between runs, apart from the Windows build in the same folder.
param(
    [Parameter(Mandatory)]
    [ValidateSet('image', 'build', 'test', 'engine', 'engine-test', 'package', 'shell', 'clean')]
    [string] $Target
)
. (Join-Path $PSScriptRoot 'common.ps1')

$image = if ($env:DOWNSTEP_DOCKER_IMAGE) { $env:DOWNSTEP_DOCKER_IMAGE } else { 'downstep-build' }
$gradleVolume = 'downstep-gradle'
$sha = [System.Security.Cryptography.SHA256]::Create()
$pathHash = -join ($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($RepoRoot.ToLowerInvariant())) | Select-Object -First 4 | ForEach-Object { $_.ToString('x2') })
$workVolume = "downstep-work-$pathHash"
$out = Join-Path $RepoRoot 'build\docker-out'

if ($Target -eq 'clean') {
    Invoke-Native docker @('volume', 'rm', '--force', $gradleVolume, $workVolume)
    if (Test-Path $out) { Remove-Item -Recurse -Force $out }
    return
}

Invoke-Native docker @('build', '--tag', $image, '--file', (Join-Path $RepoRoot 'docker\Dockerfile'), $RepoRoot)
if ($Target -eq 'image') { return }

New-Item -ItemType Directory -Force $out | Out-Null
$arguments = @('run', '--rm', '--init')
if ($Target -eq 'shell') { $arguments += '-it' }
$arguments += @(
    '--mount', "type=bind,source=$RepoRoot,target=/src,readonly",
    '--mount', "type=bind,source=$out,target=/out",
    '--mount', "type=volume,source=$gradleVolume,target=/home/builder/.gradle",
    '--mount', "type=volume,source=$workVolume,target=/work",
    '--env', 'APP_VERSION',
    $image, $Target
)
Invoke-Native docker $arguments
