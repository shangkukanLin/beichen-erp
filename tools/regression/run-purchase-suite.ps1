# Purchase line regression runner (2026-09-21): per-product charge on BOTH purchase documents
#   (purchase return + purchase exchange; direction = WE pay the supplier => POSITIVE payable).
# Composes the existing PASS/FAIL suites (backend build + frontend build + source guards + API suites
# + UI suites) into ONE invocation, in the same shape as gate.ps1: summary table, failure detail, exit 1.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\run-purchase-suite.ps1
#   ... -SkipBuild          # skip backend + frontend build (fast re-run of the suites only)
#   ... -SkipUi             # skip the slow UI suites (p11 / p11b / p11c / p3b; each drives a real browser)
#   ... -Only verify        # regex filter on the step name (e.g. -Only ui, -Only p11c, -Only api)
#
# NOTE: the API/UI suites all need the backend running, and the UI ones need the frontend built+served.
#       restart-backend.ps1 rebuilds+restarts the backend after Java changes.
# ASCII ONLY (no BOM needed => always runnable; the guard only checks ui-e2e-*.ps1, but the same
#           "Chinese without BOM => mojibake => parse error" trap applies to every .ps1).
param(
  [switch]$SkipBuild,
  [switch]$SkipUi,
  [string]$Only = ''
)
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$webRoot = Join-Path (Split-Path $root -Parent) 'beichen-erp-web'
$base = 'http://localhost:8080/api'
$rows = @()
$failDetail = @()

# ---------- 0) preflight: the backend must be up ----------
$up = $false
try {
  $r = Invoke-WebRequest -Uri "$base/auth/login" -Method Post -ContentType 'application/json' `
    -Body '{"username":"lin","password":"123","companyId":1}' -UseBasicParsing -TimeoutSec 5
  if ($r.StatusCode -eq 200) { $up = $true }
} catch { $up = $false }
if (-not $up) { Write-Output 'FAIL backend not ready -- run .\restart-backend.ps1 first'; exit 2 }
Write-Output 'PASS preflight: backend is up'

# ---------- 1) the suite list ----------
$steps = @()
if (-not $SkipBuild) {
  $steps += @{ name = 'build-backend'; kind = 'file'; file = 'be-build.ps1' }
  $steps += @{ name = 'build-frontend'; kind = 'npm' }
}
$steps += @{ name = 'guards(web-check)'; kind = 'file'; file = 'web-check.ps1' }
$steps += @{ name = 'api:charge-per-product'; kind = 'file'; file = 'verify-purchase-charge-per-product.ps1' }
$steps += @{ name = 'api:exchange'; kind = 'file'; file = 'verify-purchase-exchange.ps1' }
if (-not $SkipUi) {
  $steps += @{ name = 'ui:p11-exchange'; kind = 'file'; file = 'ui-e2e-p11-purchase-exchange.ps1' }
  $steps += @{ name = 'ui:p11b-exchange-free-paid'; kind = 'file'; file = 'ui-e2e-p11b-purchase-exchange-free-paid.ps1' }
  $steps += @{ name = 'ui:p11c-return-per-product-paid'; kind = 'file'; file = 'ui-e2e-p11c-purchase-return-per-product-paid.ps1' }
  $steps += @{ name = 'ui:p3b-return-basics'; kind = 'file'; file = 'ui-e2e-p3b-purchase-return.ps1' }
}
if ($Only -ne '') { $steps = @($steps | Where-Object { $_.name -match $Only }) }
if ($steps.Count -eq 0) { Write-Output ('FAIL no step matched -Only ' + $Only); exit 2 }

# ---------- 2) run every step, counting its own PASS/FAIL lines ----------
foreach ($s in $steps) {
  $raw = ''
  if ($s.kind -eq 'npm') {
    Push-Location $webRoot
    $out = & npm.cmd run build 2>&1
    Pop-Location
    $raw = ($out | Out-String)
  } else {
    $p = Join-Path $root $s.file
    if (-not (Test-Path $p)) {
      $rows += [pscustomobject]@{ Suite = $s.name; Pass = 0; Fail = 0; Verdict = 'SKIP(missing script)' }
      Write-Output ('--- SKIP ' + $s.name + ' (script not found: ' + $s.file + ')')
      continue
    }
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $p 2>$null
    $raw = ($out | Out-String)
  }

  $passLines = @(); $failLines = @()
  foreach ($m in [regex]::Matches($raw, '(?m)^PASS.*$')) { $passLines += $m.Value.Trim() }
  foreach ($m in [regex]::Matches($raw, '(?m)^FAIL.*$')) { $failLines += $m.Value.Trim() }

  if ($s.kind -eq 'npm') {
    # vite prints 'built in <n>s'; failures show up as 'error TS...' or 'error during build'
    $okBuild = ($raw -match 'built in') -and ($raw -notmatch 'error TS') -and ($raw -notmatch 'error during build')
    if ($okBuild) {
      $passLines = @('vite build OK'); $failLines = @()
    } else {
      $passLines = @(); $failLines = @('vite build failed (run npm run build in beichen-erp-web for details)')
    }
  }

  $verdict = 'PASS'
  if ($failLines.Count -gt 0) { $verdict = 'FAIL' }
  elseif ($passLines.Count -eq 0) { $verdict = 'NOASSERT' }
  Write-Output ('--- ' + $verdict + ' ' + $s.name + ' (pass=' + $passLines.Count + ' fail=' + $failLines.Count + ')')
  $rows += [pscustomobject]@{ Suite = $s.name; Pass = $passLines.Count; Fail = $failLines.Count; Verdict = $verdict }
  foreach ($f in $failLines) { $failDetail += ('[' + $s.name + '] ' + $f) }
}

# ---------- 3) summary ----------
Write-Output ''
Write-Output '========== purchase line regression (per-product charge) =========='
$tp = 0; $tf = 0; $bad = 0
foreach ($row in $rows) {
  $tp += $row.Pass; $tf += $row.Fail
  if ($row.Verdict -eq 'FAIL') { $bad++ }
  Write-Output ($row.Verdict.PadRight(9) + ' pass=' + ([string]$row.Pass).PadRight(5) + ' fail=' + ([string]$row.Fail).PadRight(4) + ' ' + $row.Suite)
}
Write-Output '------------------------------------------------------------------'
Write-Output ('TOTAL pass=' + $tp + ' fail=' + $tf + ' failedSuites=' + $bad)

if ($failDetail.Count -gt 0) {
  Write-Output '--- failure detail ---'
  foreach ($d in $failDetail) { Write-Output $d }
  Write-Output 'RESULT purchase suite NOT passed'
  exit 1
}
Write-Output 'RESULT purchase suite passed'
exit 0
