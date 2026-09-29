$ErrorActionPreference = 'Continue'
# Derive root from script location: <root>\outputs\scripts\run-lint.ps1
$root    = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tools   = Join-Path $root 'outputs\tools'
$reports = Join-Path $root 'outputs\reports'
New-Item -ItemType Directory -Force -Path $reports | Out-Null
$env:NODE_PATH = Join-Path $tools 'node_modules'
$redocly  = Join-Path $tools 'node_modules\.bin\redocly.cmd'
$spectral = Join-Path $tools 'node_modules\.bin\spectral.cmd'

$files = @('openapi-common.yaml','openapi-c.yaml','openapi-admin.yaml','openapi-internal.yaml')

foreach ($f in $files) {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($f)
    $abs  = Join-Path $root $f

    Write-Output "=== redocly: $f ==="
    & $redocly lint $abs --format=json 2>$null | Out-File -Encoding utf8 (Join-Path $reports "redocly-$base.json")
    Write-Output "    exit=$LASTEXITCODE"

    Write-Output "=== spectral: $f ==="
    $ruleset = Join-Path $tools '.spectral.yaml'
    & $spectral lint $abs --ruleset $ruleset --format json --output (Join-Path $reports "spectral-$base.json") 2>$null
    Write-Output "    exit=$LASTEXITCODE"
}
Write-Output "DONE"
