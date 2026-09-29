# Adds a UTF-8 BOM to the given scripts so Windows PowerShell 5.1 decodes their non-ASCII text
# correctly (without a BOM it falls back to ANSI and Chinese string literals can break parsing).
# ASCII-only by design (this helper must always be parseable regardless of encoding).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .\add-utf8-bom.ps1 <file> [<file>...]
param([Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)][string[]]$Files)
$enc = New-Object System.Text.UTF8Encoding($true)
foreach ($f in $Files) {
  if (-not (Test-Path -LiteralPath $f)) { Write-Host ('SKIP (missing) ' + $f); continue }
  $t = [System.IO.File]::ReadAllText($f)
  if ($t.Length -gt 0 -and $t[0] -eq [char]0xFEFF) { Write-Host ('ok (already BOM) ' + $f); continue }
  [System.IO.File]::WriteAllText($f, $t, $enc)
  Write-Host ('BOM added  ' + $f)
}
