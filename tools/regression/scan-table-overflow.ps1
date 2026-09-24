# Scan every menu page for tables that do not fit on one line.
# Rule (2026-09-24, user): 所有列表都要一行显示完毕，不要左右滑动.
#
# Why two checks: Element Plus clamps the table to width:100% with table-layout:fixed, so when the
# configured column widths exceed the container it does NOT add a scrollbar -- it silently CUTS off the
# right-hand columns. So both modes must be measured:
#   overflow = .el-table__body-wrapper scrollWidth - clientWidth   (>0 = a real horizontal scrollbar)
#   clip     = last body cell right edge - table right edge        (>0 = columns cut off, invisible)
# A page fails when either exceeds 2px on any visible table.
#
# The headless viewport is ~1080px => content area ~948px, which is NARROWER than the documented
# 1200px-window target => this guard is stricter than that target (what fits here fits 1200px too).
#
# Usage:  powershell -File .\scan-table-overflow.ps1                 # all pages (~3 min)
#         powershell -File .\scan-table-overflow.ps1 -Only finance   # subset (regex on the route)
# ASCII ONLY (PS 5.1 decodes a non-BOM .ps1 as GBK; param() must be the first statement).
param([string]$Only = '')

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

$routes = @(
  '/inventory/customer', '/product', '/inventory/brand', '/outsource/supplier/manage',
  '/supplier/manage', '/dev/material-type', '/outsource/material-info', '/template',
  '/dev/project', '/dev/material', '/dev/screen-model',
  '/outsource/order', '/outsource/order/delivery', '/outsource/material-order', '/outsource/material-order/delivery',
  '/outsource/return-order', '/outsource/material-return',
  '/inventory/purchase', '/inventory/purchase-return', '/inventory/purchase-exchange',
  '/inventory/sale', '/sale/return', '/sale/exchange', '/inventory/return-sort',
  '/inventory/product-stock', '/inventory/warehouse', '/inventory/stock-log',
  '/inventory/other-io', '/inventory/reclassify', '/inventory/warehouse-move', '/inventory/stock-take', '/inventory/stock-loss',
  '/finance/receivable', '/finance/payable', '/finance/bill', '/finance/cashflow', '/finance/account',
  '/finance/receipt', '/finance/payment', '/finance/expense', '/finance/invoice', '/finance/payable-transfer',
  '/system/smart', '/system/user', '/system/settings', '/system/data-manage', '/system/role', '/system/menu', '/system/clear-data',
  '/analysis/overview', '/analysis/sale', '/analysis/customer', '/analysis/purchase', '/analysis/tax', '/analysis/cash',
  '/outsource/warehouse', '/outsource/material-warehouse', '/outsource/material-stock', '/outsource/material-stock-log',
  '/outsource/material-stock-take', '/outsource/stock-loss', '/outsource/other-io', '/inventory/material-move'
)
if ($Only -ne '') { $routes = @($routes | Where-Object { $_ -match $Only }) }
Write-Host ('[SCAN] pages to visit = ' + $routes.Count + $(if ($Only -ne '') { ' (filter: ' + $Only + ')' } else { '' }))
$vw = EvalJs 'String(window.innerWidth)'
Write-Host ('[SCAN] viewport width = ' + $vw)

# Per visible table: overflow, clip, column count, row count, table width, plus the viewport width.
$js = "(()=>{const vis=e=>e.getClientRects().length>0;const out=[];const ts=[...document.querySelectorAll('.el-table')].filter(vis);ts.forEach((t,i)=>{const bw=t.querySelector('.el-table__body-wrapper');const trs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(r=>r.getClientRects().length>0);const tds=trs.length?[...trs[0].querySelectorAll('td')]:[];const tr=t.getBoundingClientRect();const last=tds.length?tds[tds.length-1].getBoundingClientRect().right:-1;out.push({i:i,ov:bw?Math.round(bw.scrollWidth-bw.clientWidth):0,clip:tds.length?Math.round(last-tr.right):0,cols:tds.length,rows:trs.length,w:Math.round(tr.width)});});return JSON.stringify({vw:window.innerWidth,tables:out})})()"

$bad = @()
$scanned = 0
$noTable = 0
foreach ($p in $routes) {
  Open $p 1800
  Start-Sleep -Milliseconds 600
  $raw = EvalJs $js
  if (-not $raw.TrimStart().StartsWith('{')) { Write-Host ('  ?? ' + $p + ' probe failed: ' + $raw); continue }
  $d = $raw | ConvertFrom-Json
  if (@($d.tables).Count -eq 0) { $noTable++; continue }
  $scanned++
  $worst = ''
  foreach ($t in @($d.tables)) {
    if ([int]$t.ov -gt 2 -or [int]$t.clip -gt 2) {
      $tag = $(if ([int]$t.ov -gt 2) { 'SCROLL' } else { 'CLIP' })
      Write-Host ('    FAIL [' + $tag + '] ' + $p + ' #' + $t.i + ' overflow=' + $t.ov + 'px clip=' + $t.clip + 'px cols=' + $t.cols + ' rows=' + $t.rows + ' tableW=' + $t.w)
      $bad += [pscustomobject]@{ page = $p; idx = $t.i; ov = $t.ov; clip = $t.clip; cols = $t.cols; rows = $t.rows; w = $t.w }
    } else {
      $worst += ('#' + $t.i + '(ov' + $t.ov + ',clip' + $t.clip + ',cols' + $t.cols + ',rows' + $t.rows + ') ')
    }
  }
  Write-Host ('  ok   ' + $p + '  ' + $worst)
}
Write-Host ''
Write-Host ('[SCAN] vw=' + $j.vw + ' pages with a table = ' + $scanned + ' ; without = ' + $noTable + ' ; offenders = ' + $bad.Count)
Ok ($bad.Count -eq 0) ('every list fits on one line (offenders: ' + $bad.Count + ')')
if ($bad.Count -gt 0) {
  Write-Host '--- offenders (sorted by clip, then overflow) ---'
  $bad | Sort-Object -Property @{Expression='clip';Descending=$true}, @{Expression='ov';Descending=$true} | ForEach-Object {
    Write-Host ('  ' + $_.page + ' #' + $_.idx + '  overflow=' + $_.ov + '  clip=' + $_.clip + '  cols=' + $_.cols + '  rows=' + $_.rows + '  tableW=' + $_.w)
  }
}
Ok ((Errs) -eq '[]') 'no JS/API errors during the sweep'
Summary 'list table one-line sweep'
