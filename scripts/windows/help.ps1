# make help and just --list: the targets of the Makefile, with the text after "##".
. (Join-Path $PSScriptRoot 'common.ps1')
Write-Host 'Targets (make <target> or just <target>):'
foreach ($line in Get-Content (Join-Path $RepoRoot 'Makefile')) {
    if ($line -match '^([a-z-]+):.*## (.*)$') {
        Write-Host ('  {0,-20} {1}' -f $Matches[1], $Matches[2])
    }
}
