$ErrorActionPreference = 'Stop'
# Pinned runtime bootstrap for Windows. Installs only inside this checkout.
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeCache = Join-Path $projectRoot '.cache/runtime'
New-Item -ItemType Directory -Force -Path $runtimeCache | Out-Null
$artifacts = @(
    @('clojure', '1.12.6', '21b578db389a8e74b286ce79c4a7f86ec641e26d88b12f18d69030916b0e18ca'),
    @('spec.alpha', '0.5.238', '94cd99b6ea639641f37af4860a643b6ed399ee5a8be5d717cff0b663c8d75077'),
    @('core.specs.alpha', '0.4.74', 'eb73ac08cf49ba840c88ba67beef11336ca554333d9408808d78946e0feb9ddb')
)
foreach ($artifact in $artifacts) {
    $artifactName, $version, $expectedHash = $artifact
    $destination = Join-Path $runtimeCache "$artifactName-$version.jar"
    if (-not (Test-Path $destination)) {
        $temporary = Join-Path $runtimeCache ([System.IO.Path]::GetRandomFileName())
        try {
            $uri = "https://repo.maven.apache.org/maven2/org/clojure/$artifactName/$version/$artifactName-$version.jar"
            Invoke-WebRequest -Uri $uri -OutFile $temporary
            if ((Get-FileHash -Algorithm SHA256 $temporary).Hash.ToLowerInvariant() -ne $expectedHash) {
                throw "Download checksum mismatch: $artifactName"
            }
            Move-Item -Path $temporary -Destination $destination -Force
        } finally {
            if (Test-Path $temporary) { Remove-Item $temporary }
        }
    }
    if ((Get-FileHash -Algorithm SHA256 $destination).Hash.ToLowerInvariant() -ne $expectedHash) {
        throw "Cached runtime checksum mismatch: $destination"
    }
}
$classpath = @(
    (Join-Path $runtimeCache '*'),
    (Join-Path $projectRoot 'src'),
    (Join-Path $projectRoot 'resources'),
    (Join-Path $projectRoot 'test'),
    (Join-Path $projectRoot 'dev')
) -join [System.IO.Path]::PathSeparator
Push-Location $projectRoot
try {
    & java '-Dfile.encoding=UTF-8' '-cp' $classpath 'clojure.main' @args
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
