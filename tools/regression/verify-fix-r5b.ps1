# R5b verification (2026-09-20): F7-188 / F7-196 -- ECharts instances are now disposed on unmount.
#
# What can actually break: if dispose ran at the WRONG time the charts would render blank.
# So the assertion is "each chart page still mounts at least one live ECharts instance".
# ECharts tags its container with the `_echarts_instance_` attribute, which is present while the
# instance is alive (and removed by dispose) -> a reliable probe for "the chart is really rendered".
#
# Note on keep-alive: onUnmounted only fires when the component is REALLY destroyed (evicted from
# the keep-alive cache or excluded by route) -- it does NOT fire when a cached tab is switched away,
# so these handlers cannot break normal tab switching.
#
# ASCII ONLY.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

function ChartCount() {
  return (EvalJs "String(document.querySelectorAll('[_echarts_instance_]').length)")
}

function CheckChartPage([string]$path, [string]$label) {
  Step ($label + ' -> ' + $path)
  Open $path 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2500
  $n = ChartCount
  Write-Host ('  live echarts instances = ' + $n)
  Write-Host ('  path now = ' + (EvalJs 'String(location.pathname)'))
  Ok ("$n" -match '^\d+$' -and [int]"$n" -gt 0) ($label + ' rendered at least one live chart')
  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') ($label + ' recorded no JS/API errors')
}

if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  CheckChartPage '/analysis/overview' 'analysis overview (F7-188 trendChart)'
  # NOTE: do NOT require a live chart on /dashboard -- its trend chart only renders inside the
  # "overview" tab while the page deliberately defaults to the "memo" tab, so 0 instances is correct.
  Step 'dashboard (F7-196) -> /dashboard'
  Open '/dashboard' 3500
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 2000
  Write-Host ('  bodyLen = ' + (EvalJs "String((document.body.innerText||'').length)"))
  Write-Host ('  path now = ' + (EvalJs 'String(location.pathname)'))
  Ok ((EvalJs "String(document.querySelectorAll('.el-tabs,.el-card,.el-table').length)") -match '[1-9]') 'dashboard rendered (containers present)'
  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'dashboard recorded no JS/API errors'
  Summary 'R5b charts part 1'
}

if ($Part -eq 0 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  CheckChartPage '/analysis/cash' 'analysis cash (F7-188 cashChart)'
  CheckChartPage '/analysis/tax' 'analysis tax (F7-188 taxChart)'
  CheckChartPage '/analysis/sale' 'analysis sale (F7-188 pieRefs)'
  Summary 'R5b charts part 2'
}

if ($Part -eq 0 -or $Part -eq 3) {
  EnsureLogin | Out-Null
  CheckChartPage '/analysis/purchase' 'analysis purchase (F7-188 chart + pieRefs)'
  CheckChartPage '/analysis/customer/1' 'analysis customer profile (F7-188 four charts)'
  Summary 'R5b charts part 3'
}
