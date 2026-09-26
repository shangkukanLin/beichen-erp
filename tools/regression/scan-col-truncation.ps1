# Guard: list columns whose cell text is CUT OFF (ellipsized) instead of shown in full.
# Rule (2026-09-25, user): list data must be shown as completely as possible.
#   - identifier columns (code / partner / warehouse / date / status) must never be cut;
#   - multi-value summary columns (product / material / item summary) have unbounded length, so
#     ellipsis is allowed there -- they must carry a tooltip and the row is clickable into detail.
#
# Why this is separate from scan-table-overflow.ps1:
#   that guard only proves "the table does not scroll horizontally" (colSum <= avail). A table can fit
#   perfectly and still cut every cell, because Element Plus ellipsizes text cells by design -- which is
#   exactly what the user reported. This one measures, per column, how many body cells have
#   .cell.scrollWidth > .cell.clientWidth (i.e. the text is visually cut).
#
# 2026-09-26 B10 -- added HEADER check (the earlier version had a blind spot the user hit twice:
#   a column squeezed below its own label width shows "退货..." / "是否..." instead of "退货金额" /
#   "是否缺料", while the body cells were short enough to pass). Header clipping = FAIL, no whitelist.
#
# FAIL when a non-whitelisted column has clipped cells, or when any cell wraps onto a second line.
# The whitelist lives in ui-e2e-zh.json -> col_allow_truncate, because this file must stay ASCII
# (PS 5.1 decodes a non-BOM .ps1 as GBK, so Chinese labels here would be mangled).
#
# Scope: the outsource list pages fixed on 2026-09-25. Extend $routes as other pages are done.
param([string]$Only = '', [string]$Report = "$env:TEMP\col-truncation.json")

. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

$routes = @(
  # --- 2026-09-25 委外加工（样板批）---
  '/outsource/order',
  '/outsource/order/delivery',
  '/outsource/return-order',
  '/outsource/material-order',
  '/outsource/material-order/delivery',
  '/outsource/material-return',
  # --- 2026-09-25 B1 基础数据 + 研发 ---
  '/inventory/customer',
  '/product',
  '/inventory/brand',
  '/outsource/supplier/manage',
  '/supplier/manage',
  '/dev/material-type',
  '/outsource/material-info',
  '/template',
  '/dev/project',
  '/dev/material',
  # --- 2026-09-25 B2 屏幕资料（列表精简为 7 列，全部完整）---
  '/dev/screen-model',
  # --- 2026-09-25 B3 采购 ---
  '/inventory/purchase',
  '/inventory/purchase-return',
  '/inventory/purchase-exchange',
  # --- 2026-09-26 B4 销售（含退货整理）---
  '/inventory/sale',
  '/sale/return',
  '/sale/exchange',
  '/inventory/return-sort',
  # --- 2026-09-26 B5a 成品库存 + 移仓（进销存）---
  '/inventory/product-stock',
  '/inventory/warehouse',
  '/inventory/stock-log',
  '/inventory/other-io',
  '/inventory/reclassify',
  '/inventory/warehouse-move',
  '/inventory/stock-take',
  '/inventory/stock-loss',
  '/inventory/material-move',
  # --- 2026-09-26 B5b 物料仓库 ---
  '/outsource/warehouse',
  '/outsource/material-warehouse',
  '/outsource/material-stock',
  '/outsource/material-stock-log',
  '/outsource/material-stock-take',
  '/outsource/stock-loss',
  '/outsource/other-io',
  # --- 2026-09-26 B6 财务 ---
  '/finance/receivable',
  '/finance/payable',
  '/finance/receipt',
  '/finance/payment',
  '/finance/bill',
  '/finance/cashflow',
  '/finance/account',
  '/finance/expense',
  '/finance/invoice',
  '/finance/payable-transfer',
  # --- 2026-09-26 B7 系统 + 分析 ---
  '/system/user',
  '/system/role',
  '/system/menu',
  '/analysis/overview',
  '/analysis/cash',
  '/analysis/tax',
  '/analysis/sale',
  '/analysis/customer',
  '/analysis/purchase'
)
if ($Only -ne '') { $routes = @($routes | Where-Object { $_ -match $Only }) }

$allow = @()
foreach ($t in ([string](ZH 'col_allow_truncate')) -split ',') {
  $t = $t.Trim(); if ($t -ne '') { $allow += $t }
}
Write-Host ('[SCAN] pages = ' + $routes.Count + ' ; whitelisted (allowed to ellipsize) cols = ' + ($allow -join ' | '))

# Per visible table, per column: header label, rendered width, # clipped cells, widest needed px, sample.
# NOTE: the whitelist is compared INSIDE the browser (labels are Chinese; PowerShell reads our stdout as
# GBK, so Chinese coming back from the page would never string-match a PS variable).
$allowB64 = B64 ([string](ZH 'col_allow_truncate'))
# Labels/samples come back BASE64: PowerShell reads our stdout as GBK, so raw Chinese would be mangled
# before it reaches the JSON report. btns = # cells in that column already containing a button/link
# (i.e. "this column is already clickable") -- useful when planning the click-through work.
$js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=s=>btoa(unescape(encodeURIComponent(s||'')));const AL=T('$allowB64').split(',').map(s=>s.trim()).filter(Boolean);const vis=e=>e.getClientRects().length>0;const out=[];const ts=[...document.querySelectorAll('.el-table')].filter(vis);ts.forEach((t,ti)=>{const hr=t.querySelector('.el-table__header tr:last-child');const ths=hr?[...hr.querySelectorAll('th')]:[];const rows=[...t.querySelectorAll('.el-table__body tbody tr')].filter(r=>r.getClientRects().length>0);const cols=ths.map((th,ci)=>{const lb=((th.querySelector('.cell')||th).innerText||'').trim();let clipped=0,wrap=0,btns=0,sample='',need=0;rows.forEach(r=>{const tds=r.querySelectorAll('td');if(ci>=tds.length)return;const td=tds[ci];const c=td.querySelector('.cell');if(!c)return;if(td.querySelector('.el-button,.el-link'))btns++;const sw=c.scrollWidth,cw=c.clientWidth;const txt=(c.innerText||'').trim();if(sw-cw>1){clipped++;if(sw>need){need=sw;sample=txt}}if(c.getBoundingClientRect().height>26)wrap++;});const w=th?Math.round(th.getBoundingClientRect().width):0;const hc=th?th.querySelector('.cell'):null;const hsw=hc?hc.scrollWidth:0,hcw=hc?hc.clientWidth:0;const hdrClip=(hc&&hsw-hcw>1)?1:0;return {label:B(lb),sample:B(sample.slice(0,28)),width:w,clipped:clipped,wrapped:wrap,btns:btns,need:need>0?need+18:0,hdrClip:hdrClip,hdrNeed:hdrClip?hsw+18:0,allowed:AL.indexOf(lb)>=0};});out.push({idx:ti,cols:ths.length,rows:rows.length,detail:cols});});return JSON.stringify(out)})()"

function Dec([string]$b) {
  if ([string]::IsNullOrEmpty($b)) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) } catch { return '?' }
}

$bad = @()
$reportRows = @()
$scanned = 0
$noTable = 0
# 某些页表格要等接口回来才渲染（退货整理 ~3s）⇒ 首次测不到就加长等待重测一次
$slowPages = @('/inventory/return-sort')
foreach ($p in $routes) {
  Open $p 2000
  Start-Sleep -Milliseconds 500
  $raw = EvalJs $js
  if (-not $raw.TrimStart().StartsWith('[')) { Write-Host ('  ?? ' + $p + ' probe failed: ' + $raw); continue }
  $tables = $raw | ConvertFrom-Json
  if (@($tables).Count -eq 0 -and ($slowPages -contains $p)) {
    Open $p 3500
    Start-Sleep -Milliseconds 800
    $raw = EvalJs $js
    if ($raw.TrimStart().StartsWith('[')) { $tables = $raw | ConvertFrom-Json }
  }
  if (@($tables).Count -eq 0) { $noTable++; continue }
  $scanned++
  foreach ($t in @($tables)) {
    $note = ''
    foreach ($c in @($t.detail)) {
      $label = Dec ([string]$c.label)
      $allowed = [bool]$c.allowed
      $tag = ''
      # Clipped HEADER is the worst case (the column name itself unreadable): highest priority,
      # and it can NOT be whitelisted (a whitelist entry is about long cell content, never the label).
      if ([int]$c.hdrClip -gt 0) { $tag = 'HDRCLIP' }
      elseif ([int]$c.wrapped -gt 0) { $tag = 'WRAPPED' }
      elseif ([int]$c.clipped -gt 0 -and -not $allowed) { $tag = 'CLIPPED' }
      $reportRows += [pscustomobject]@{
        page = $p; table = $t.idx; label = $label; width = [int]$c.width
        clipped = [int]$c.clipped; wrapped = [int]$c.wrapped; need = [int]$c.need
        hdrClip = [int]$c.hdrClip; hdrNeed = [int]$c.hdrNeed
        allowed = $allowed; btns = [int]$c.btns; sample = (Dec ([string]$c.sample)); rows = [int]$t.rows
      }
      if ($tag -ne '') {
        $extra = if ([int]$c.hdrClip -gt 0) { ' hdrNeed=' + $c.hdrNeed } else { '' }
        Write-Host ('    FAIL [' + $tag + '] ' + $p + ' table#' + $t.idx + ' col=' + $label + ' width=' + $c.width + ' clipped=' + $c.clipped + ' need=' + $c.need + $extra + ' rows=' + $t.rows)
        $bad += [pscustomobject]@{ page = $p; table = $t.idx; label = $label; width = [int]$c.width; clipped = [int]$c.clipped; wrapped = [int]$c.wrapped; need = [int]$c.need; sample = [string]$c.sample }
      }
      $mark = ''
      if ([int]$c.hdrClip -gt 0) { $mark = $mark + '!HDR' }
      if ([int]$c.clipped -gt 0) { $mark = $mark + '!x' + $c.clipped }
      if ($allowed) { $mark = $mark + '*allow' }
      $note = $note + $label + '(' + $c.width + $mark + ') '
    }
    Write-Host ('  ok   ' + $p + ' #' + $t.idx + ' cols=' + $t.cols + ' rows=' + $t.rows + '  ' + $note)
  }
}
[IO.File]::WriteAllText($Report, ($reportRows | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ''
Write-Host ('[SCAN] pages with a table = ' + $scanned + ' ; without = ' + $noTable + ' ; offenders = ' + $bad.Count + ' ; report = ' + $Report)
Ok ($bad.Count -eq 0) ('no cut-off column (offenders: ' + $bad.Count + ')')
if ($bad.Count -gt 0) {
  Write-Host '--- offenders ---'
  $bad | ForEach-Object { Write-Host ('  ' + $_.page + ' #' + $_.table + '  ' + $_.label + '  width=' + $_.width + '  clipped=' + $_.clipped + '  need=' + $_.need + '  sample=' + $_.sample) }
}
Ok ((Errs) -eq '[]') 'no JS/API errors during the sweep'
Summary 'list column truncation sweep'
