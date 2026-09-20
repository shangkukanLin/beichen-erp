# R5c verification (2026-09-20): dead-code removal (F7-146/155/158/176/180/189/190),
# enum centralisation (F7-165), defineOptions names (F7-159) and the dashboard wording (F7-195).
#
# Smoke only: every touched page must still render and log no JS/API errors. That is the real risk
# here -- the batch deleted variables/functions and rewired a select's options, so "page still works"
# plus a green build is exactly what needs proving.
#
# ASCII ONLY.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function WaitVisible([string]$selector, [int]$timeoutMs = 20000) {
  $js = "String([...document.querySelectorAll('$selector')].filter(e=>e.getClientRects().length>0).length)"
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.ElapsedMilliseconds -lt $timeoutMs) {
    $n = EvalJs $js
    if ("$n" -match '^\d+$' -and [int]"$n" -gt 0) { return "$n" }
    Start-Sleep -Milliseconds 700
  }
  return 'TIMEOUT'
}

# NOTE: '/material' and '/memo' were dropped from this list -- both land on /403 for the super-admin
# account used here (their menus are not mounted for this tenant), which is a PICKING problem, not a
# regression. '/analysis/overview' is verified in verify-fix-r5b.ps1 (charts) and kept here as the
# slow first-paint case.
$lists = @(
  '/inventory/reclassify',
  '/analysis/overview',
  '/analysis/sale',
  '/analysis/purchase',
  '/finance/cashflow',
  '/finance/invoice',
  '/finance/payment',
  '/dashboard'
)
$adds = @(
  '/inventory/warehouse-move/add',
  '/inventory/other-io/add',
  '/dev/project/add',
  '/outsource/order/add'
)

EnsureLogin | Out-Null

if ($Part -eq 0 -or $Part -eq 1) {
  Step 'R5c smoke: touched list/analysis/finance pages'
  foreach ($p in $lists) {
    Open $p 3000
    $c = WaitVisible '.el-card, .el-tabs, .el-form, .el-table, .el-collapse, .el-descriptions'
    $errs = Errs
    Write-Host ('  ' + $p + ' containers=' + $c + ' errs=' + $errs)
    Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($p + ' rendered')
    Ok ($errs -eq '[]') ($p + ' no JS/API errors')
  }
  Summary 'R5c smoke lists'
}

if ($Part -eq 0 -or $Part -eq 3) {
  Step 'R5c smoke: re-check the three pages that timed out (slower wait, print landing path)'
  foreach ($p in @('/material', '/memo', '/analysis/overview')) {
    Open $p 6000
    Write-Host ('  path now = ' + (EvalJs 'String(location.pathname)'))
    Start-Sleep -Milliseconds 2500
    $c = WaitVisible '.el-card, .el-tabs, .el-form, .el-table, .el-collapse, .el-descriptions'
    $errs = Errs
    Write-Host ('  ' + $p + ' containers=' + $c + ' errs=' + $errs)
    Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($p + ' rendered')
    Ok ($errs -eq '[]') ($p + ' no JS/API errors')
  }
  Summary 'R5c smoke retry'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Step 'R5c smoke: add pages that gained defineOptions (script top touched)'
  foreach ($p in $adds) {
    Open $p 3000
    $c = WaitVisible '.el-card, .el-tabs, .el-form, .el-table'
    $errs = Errs
    Write-Host ('  ' + $p + ' containers=' + $c + ' errs=' + $errs)
    Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($p + ' rendered')
    Ok ($errs -eq '[]') ($p + ' no JS/API errors')
  }
  Summary 'R5c smoke add pages'
}
