& (Join-Path $PSScriptRoot 'clj.ps1') '-m' 'hazel.cli' @args
exit $LASTEXITCODE
