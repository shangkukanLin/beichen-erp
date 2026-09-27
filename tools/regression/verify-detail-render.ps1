# Detail-page RENDER smoke guard (2026-09-27).
#
# WHY: a setup-time exception in a `.vue` blanks the WHOLE app -- `#app` innerHTML = 0, no vite error overlay,
#   the module still compiles (HTTP 200) and the route still resolves. Compile/HTTP-only guards cannot see it.
#   The real incident: `watch(computedA, cb)` was placed ABOVE the consts that computedA depends on; `watch()`
#   evaluates the source ONCE during setup (even without `immediate`) -> "Cannot access 'x' before initialization"
#   (TDZ) -> blank page on every deep link to the detail page.
#
# WHAT IT CHECKS (representative detail pages, ids resolved from the DB):
#   1. `#app` actually rendered (innerHTML length > 1000; a blank app is 0)
#   2. the console logged no "Unhandled error during execution of setup function"
# PURE ASCII.
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = @($o | ForEach-Object { "$_" } | Where-Object { $_.Trim() -ne '' })
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin | Out-Null

$cases = @(
  @{ note = 'outsource return-order REPAIR detail'; url = ('/outsource/return-order/detail/' + (SqlOne "SELECT id FROM outsource_return_order WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'outsource return-order DEFECT detail'; url = ('/outsource/return-order/detail/' + (SqlOne "SELECT id FROM outsource_return_order WHERE return_type='DEFECT' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'material-return REPAIR detail'; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REPAIR' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'material-return REFUND detail'; url = ('/outsource/material-return/detail/' + (SqlOne "SELECT id FROM outsource_material_return WHERE return_type='REFUND' ORDER BY id DESC LIMIT 1")) },
  @{ note = 'outsource order detail (untouched control)'; url = ('/outsource/order/detail/' + (SqlOne "SELECT id FROM outsource_order ORDER BY id DESC LIMIT 1")) }
)

foreach ($c in $cases) {
  Step $c.note
  if ($c.url -match '/0$' -or $c.url -match '/$') { Info ('no document id found for ' + $c.url + ' -> skipped (coverage gap)'); continue }
  agent-browser console --clear | Out-Null
  Open $c.url 4200
  $len = [int](EvalJs "String((document.querySelector('#app')||{innerHTML:''}).innerHTML.length)")
  $hdr = EvalJs "(()=>{const e=document.querySelector('.page-header__title');return e?(e.innerText||'').trim():'NONE'})()"
  $con = (agent-browser console 2>&1 | ForEach-Object { "$_" }) -join ' '
  Write-Host ('  url=' + $c.url + ' appHtml=' + $len + ' pageHeader=' + $hdr)
  Ok ($len -gt 1000) ($c.note + ': the app rendered (appHtml > 1000)')
  Ok (-not ($con -match 'execution of setup function')) ($c.note + ': no Vue setup error in console')
}

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-detail-render (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
