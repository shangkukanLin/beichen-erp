# Scan every menu page for tables that do not fit on one line.
# Rule (2026-09-24, user): 所有列表都要一行显示完毕，不要左右滑动.
#
# 2026-09-24 v2 -- why v1 was WRONG (it reported /inventory/purchase as OK while the user saw scrolling):
#   v1 measured `.el-table__body-wrapper` scrollWidth-clientWidth and "last cell right - table right".
#   Both are blind for the way this app renders tables:
#     (a) Element Plus 2.x scrolls inside `.el-scrollbar__wrap`; the outer `.el-table__body-wrapper`
#         has overflow:hidden => scrollWidth-clientWidth is 0 even when 284px is cut off.
#     (b) every list pins its operation column with fixed="right" (sticky) => the last cell always sits
#         flush with the container's right edge => the "clip" number is always 0.
#   => v1 only ever caught tables WITHOUT a fixed column. Use a DOM-independent criterion instead:
#      sum(rendered column widths) vs the table's available width.
#
# Criterion per visible table:
#   colSum = sum of the rendered header cell widths
#   avail  = table clientWidth (what the columns have to fit into)
#   margin = avail - colSum      (>0 = fits with slack, <0 = columns stick out)
#   scroll = max over [.el-table__body-wrapper, .el-scrollbar__wrap] of scrollWidth-clientWidth
#   btnClip= max overflow of a cell that CONTAINS BUTTONS (operation columns) -- text cells ellipsize by
#            design (show-overflow-tooltip), but an operation column that cannot fit its buttons shows
#            "..." and the action becomes unreachable => checked separately (2026-09-24: the fixed
#            purchase list fit the table but its 4th button was cut, revealed by a screenshot).
#   FAIL when margin < -2, or scroll > 2, or btnClip > 2.
#
# The headless viewport is 1262 wide => content area ~956px (the documented target is a 1200px window,
# i.e. ~894px content: this guard is stricter than 1280 but slightly looser than 1200).
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
  # 2026-09-27 三级菜单叶子（原来只扫了两级页面）；/outsource/return-back 已下线（重定向口径另由 nav 脚本覆盖）
  # 2026-09-29：/outsource/return-order（关联退货叶子）已下线 ⇒ 从本清单移除（它现在重定向到下面的 unlinked 叶子）
  '/outsource/return-order/unlinked', '/outsource/return-order/repair',
  '/outsource/material-return', '/outsource/material-return/unlinked', '/outsource/material-return/repair',
  '/inventory/purchase', '/inventory/purchase-return', '/inventory/purchase-exchange',
  '/inventory/sale', '/sale/return', '/sale/exchange', '/inventory/return-sort',
  '/inventory/product-stock', '/inventory/warehouse', '/inventory/stock-log',
  '/inventory/other-io', '/inventory/reclassify', '/inventory/warehouse-move', '/inventory/stock-take', '/inventory/stock-loss',
  '/finance/receivable', '/finance/payable', '/finance/bill', '/finance/cashflow', '/finance/account',
  '/finance/receipt', '/finance/payment', '/finance/expense', '/finance/invoice', '/finance/payable-transfer',
  '/system/smart', '/system/user', '/system/settings', '/system/data-manage', '/system/role', '/system/menu', '/system/clear-data',
  '/analysis/overview', '/analysis/sale', '/analysis/customer', '/analysis/product', '/analysis/purchase', '/analysis/tax', '/analysis/cash',
  '/outsource/warehouse', '/outsource/material-warehouse', '/outsource/material-stock', '/outsource/material-stock-log',
  '/outsource/material-stock-take', '/outsource/stock-loss', '/outsource/other-io', '/inventory/material-move'
)
if ($Only -ne '') { $routes = @($routes | Where-Object { $_ -match $Only }) }
Write-Host ('[SCAN] pages to visit = ' + $routes.Count + $(if ($Only -ne '') { ' (filter: ' + $Only + ')' } else { '' }))
$vw = EvalJs 'String(window.innerWidth)'
Write-Host ('[SCAN] viewport width = ' + $vw)

# Per visible table: colSum / avail / margin / scroll + column count and row count.
$js = "(()=>{const vis=e=>e.getClientRects().length>0;const out=[];const ts=[...document.querySelectorAll('.el-table')].filter(vis);ts.forEach((t,i)=>{const hr=t.querySelector('.el-table__header tr:last-child');const ths=hr?[...hr.querySelectorAll('th')]:[];let sum=0;const cw=[];ths.forEach(th=>{const w=Math.round(th.getBoundingClientRect().width);cw.push(w);sum+=w;});const avail=Math.round(t.clientWidth);const cands=[t.querySelector('.el-table__body-wrapper')];[...t.querySelectorAll('.el-scrollbar__wrap')].forEach(x=>cands.push(x));let sc=0;cands.forEach(x=>{if(!x)return;const d=x.scrollWidth-x.clientWidth;if(d>sc)sc=Math.round(d);});const trs=[...t.querySelectorAll('.el-table__body tbody tr')].filter(r=>r.getClientRects().length>0);let bc=0;trs.forEach(r=>{const tds=r.querySelectorAll('td');if(!tds.length)return;const td=tds[tds.length-1];if(!td.querySelector('.el-button'))return;const c=td.querySelector('.cell');if(!c)return;const d=c.scrollWidth-c.clientWidth;if(d>bc)bc=Math.round(d);});out.push({i:i,cols:cw.length,rows:trs.length,colSum:sum,avail:avail,margin:avail-sum,scroll:sc,btnClip:bc,cw:cw});});return JSON.stringify({vw:window.innerWidth,tables:out})})()"

$bad = @()
$scanned = 0
$noTable = 0
$probeFail = @()
foreach ($p in $routes) {
  Open $p 1800
  Start-Sleep -Milliseconds 600
  $raw = EvalJs $js
  # 2026-10-05 F7-293: a failed probe (browser/frontend not up, page crashed) used to be `continue`d -- i.e.
  # invisible. With every probe failing the sweep still reported PASS ("0 offenders"). Now probe failures are
  # collected and asserted to be zero, and a sweep that measured nothing at all is a failure too.
  if (-not $raw.TrimStart().StartsWith('{')) { Write-Host ('  ?? ' + $p + ' probe failed: ' + $raw); $probeFail += $p; continue }
  $d = $raw | ConvertFrom-Json
  if (@($d.tables).Count -eq 0) { $noTable++; continue }
  $scanned++
  $summary = ''
  foreach ($t in @($d.tables)) {
    $summary += ('#' + $t.i + '(cols' + $t.cols + ' sum' + $t.colSum + ' avail' + $t.avail + ' margin' + $t.margin + ' scroll' + $t.scroll + ' btnClip' + $t.btnClip + ') ')
    if ([int]$t.margin -lt -2 -or [int]$t.scroll -gt 2 -or [int]$t.btnClip -gt 2) {
      $tag = $(if ([int]$t.btnClip -gt 2) { 'BTNCLIP' } elseif ([int]$t.scroll -gt 2) { 'SCROLL' } else { 'OVERWIDE' })
      Write-Host ('    FAIL [' + $tag + '] ' + $p + ' #' + $t.i + ' colSum=' + $t.colSum + ' avail=' + $t.avail + ' margin=' + $t.margin + ' scroll=' + $t.scroll + ' btnClip=' + $t.btnClip + ' cols=' + $t.cols + ' rows=' + $t.rows)
      $bad += [pscustomobject]@{ page = $p; idx = $t.i; colSum = [int]$t.colSum; avail = [int]$t.avail; margin = [int]$t.margin; scroll = [int]$t.scroll; btnClip = [int]$t.btnClip; cols = [int]$t.cols; cw = ($t.cw -join ',') }
    }
  }
  Write-Host ('  ok   ' + $p + '  ' + $summary)
}
Write-Host ''
Write-Host ('[SCAN] pages with a table = ' + $scanned + ' ; without = ' + $noTable + ' ; offenders = ' + $bad.Count + ' ; probeFailures = ' + $probeFail.Count)
Ok ($probeFail.Count -eq 0) ('every page could be probed (failures: ' + $probeFail.Count + (if ($probeFail.Count -gt 0) { ' -> ' + ($probeFail -join ', ') } else { '' }) + ')')
Ok ($scanned -gt 0) ('at least one page actually had a table to measure (measured: ' + $scanned + ')')
Ok ($bad.Count -eq 0) ('every list fits on one line (offenders: ' + $bad.Count + ')')
if ($bad.Count -gt 0) {
  Write-Host '--- offenders (worst margin first) ---'
  $bad | Sort-Object margin | ForEach-Object {
    Write-Host ('  ' + $_.page + ' #' + $_.idx + '  colSum=' + $_.colSum + '  avail=' + $_.avail + '  margin=' + $_.margin + '  scroll=' + $_.scroll + '  btnClip=' + $_.btnClip + '  cols=' + $_.cols + '  widths=' + $_.cw)
  }
}
Ok ((Errs) -eq '[]') 'no JS/API errors during the sweep'
Summary 'list table one-line sweep'
