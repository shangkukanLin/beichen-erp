# temporary runner: the two site-wide layout scans, sequentially, into one log. ASCII ONLY.
$ErrorActionPreference = 'Continue'
$root = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\tools\regression'
$log = Join-Path $root 'audit\tmp-scan.log'
Remove-Item $log -Force -ErrorAction SilentlyContinue
foreach ($g in @('scan-table-overflow.ps1', 'scan-col-truncation.ps1')) {
  Add-Content -Path $log -Value ('##### ' + $g) -Encoding UTF8
  $o = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root $g) 2>&1
  @($o) | Select-String -Pattern '^PASS|^FAIL|^RESULT|^=====|FAIL|OVERWIDE|CLIPPED|HDRCLIP|HDRTIGHT|WRAPPED' |
    ForEach-Object { Add-Content -Path $log -Value ("$_") -Encoding UTF8 }
  Add-Content -Path $log -Value ('##### exit=' + $LASTEXITCODE) -Encoding UTF8
}
Add-Content -Path $log -Value 'done' -Encoding UTF8
