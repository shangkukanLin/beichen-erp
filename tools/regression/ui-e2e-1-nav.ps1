# Temp test 1: navigation smoke over every menu page (UI level, no data writes)
#
# 2026-09-19 upgrade (user report: "clicking the 整理单 tab left only 首页 in the top breadcrumb"):
#   The top bar is a BREADCRUMB built from the active tab path. Tabs used to be keyed by route.fullPath
#   (query included) while menus are keyed by routePath (path only) => any page whose URL carried a query
#   right after the menu path (/inventory/return-sort?tab=bills, /template?tab=contract, /dev/project?tab=active)
#   lost its menu chain and the breadcrumb collapsed to 首页. Two fixes landed:
#     (1) tab identity = query-free path (stores/tabs.ts, self-heals old localStorage, keeps fullPath for state restore)
#     (2) layout breadcrumb normalizes query/hash away before matching menus (layout/index.vue)
#   This script now asserts, for EVERY page: >=2 breadcrumb levels, no duplicated tab label, and the SAME
#   breadcrumb when the page is opened with a query string (the regression class above) + a few real deep links.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

# 2026-09-19: deterministic start. The top tab bar persists in localStorage (beichen_tabs) and the sweep below
# opens one tab per page, so every run must start from a clean slate - otherwise the "no duplicated tab"
# assertion would be measuring leftovers of earlier runs. The script cleans up again at the end.
$tabCountJs = "(()=>{const vis=e=>e.getClientRects().length>0;return String([...document.querySelectorAll('.tab-bar .tab-item')].filter(vis).length)})()"
EvalJs "localStorage.removeItem('beichen_tabs');'ok'" | Out-Null
Open '/dashboard' 2200
Write-Host ('[RESET] tab bar starts with ' + (EvalJs $tabCountJs) + ' tab(s)')

$routes = @(
  '/dashboard', '/inventory/customer', '/product', '/inventory/brand', '/outsource/supplier/manage',
  '/supplier/manage', '/dev/material-type', '/outsource/material-info', '/template',
  '/dev/project', '/dev/material', '/dev/screen-model',
  '/outsource/order', '/outsource/order/delivery', '/outsource/material-order', '/outsource/material-order/delivery',
  '/outsource/return-order', '/outsource/material-return', '/supplier/manage',
  # 2026-09-27 三级菜单叶子（加工退货 3 叶子 + 物料退货 3 叶子）；
  #   /outsource/return-back 已下线（2026-09-27 用户口径「加工返回单多余了」）⇒ 保留在此**专测旧地址重定向**
  #   （重定向到 /outsource/return-order/unlinked，页签身份随后者 ⇒ 不会产生同名重复页签）
  # 2026-09-29：/outsource/return-order（关联退货叶子）同样已下线 ⇒ 也留在这里**专测重定向**（同落到 unlinked）
  '/outsource/return-order/unlinked', '/outsource/return-order/repair', '/outsource/return-back',
  '/outsource/material-return/unlinked', '/outsource/material-return/repair',
  '/inventory/purchase', '/inventory/purchase-return', '/inventory/purchase-exchange',
  '/inventory/sale', '/sale/return', '/sale/exchange', '/inventory/return-sort',
  '/inventory/product-stock', '/inventory/warehouse', '/inventory/stock-log',
  '/inventory/other-io', '/inventory/reclassify', '/inventory/warehouse-move', '/inventory/stock-take', '/inventory/stock-loss',
  '/finance/receivable', '/finance/payable', '/finance/bill', '/finance/cashflow', '/finance/account',
  '/finance/receipt', '/finance/payment', '/finance/expense', '/finance/invoice', '/finance/payable-transfer',
  '/system/smart', '/system/user', '/system/settings', '/system/data-manage', '/system/role', '/system/menu', '/system/clear-data',
  '/analysis/overview', '/analysis/sale', '/analysis/customer', '/analysis/product', '/analysis/purchase', '/analysis/tax', '/analysis/cash',
  '/outsource/warehouse', '/outsource/material-warehouse', '/outsource/material-stock', '/outsource/material-stock-log', '/outsource/material-stock-take', '/outsource/stock-loss',
  '/outsource/other-io', '/inventory/material-move'
)

# real deep links that carry a query on a menu path (the class that used to break the breadcrumb)
$deepLinks = @(
  '/inventory/return-sort?tab=bills',
  '/template?tab=phase', '/template?tab=contract',
  '/dev/project?tab=active', '/dev/project?tab=finished',
  '/outsource/material-info?materialTypeId=1'
)

$errKeys = @('err_sys', 'err_nofunc', 'err_403', 'err_fail', 'err_loading')
$script:bad = 0
$script:bcBad = 0
$script:dupBad = 0
$script:qBad = 0
$script:memBad = 0
$script:locBad = 0

# 2026-09-21 locale guard: Element Plus built-in texts MUST be Chinese (main.ts: app.use(ElementPlus, { locale: zhCn })).
# Regression class: the app shipped WITHOUT a locale, so confirm boxes read Cancel / OK, pagination read "Total 5 10/page",
# tables read "No Data" and the date panel read English months. Checked on every page of this sweep.
# NOTE: this file is ASCII-only on purpose (PS 5.1 mangles UTF-8 without BOM) => expectations are built with [char].
$CH_GONG = [string][char]0x5171                                            # "gong" (total label)
# 2026-10-03: EvalJs 取回的中文在本机被按 GBK 解码（同一坑已记录在 verify-bill-export.ps1:67）⇒ 探针必须回 base64。
# 原实现拿返回值直接比 $CH_GONG，永远不相等 ⇒ localeBad 恒等于页面数，这个检查既刷不出"真中文"，
# 也就永远抓不到它本该守的回归（语言包丢失 => 分页/空态变英文）。ASCII only.
function Dec([string]$b) {
  if ([string]::IsNullOrEmpty($b)) { return '' }
  try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) } catch { return '?' }
}
$locJs = "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));const p=document.querySelector('.el-pagination');const t=p?(p.innerText||'').replace(/\s+/g,' ').trim():'';const e=[...document.querySelectorAll('.el-table__empty-text')].map(x=>B((x.innerText||'').trim())).filter(x=>x);return JSON.stringify({pg:B(t),empty:e.slice(0,3)});})()"

# breadcrumb items + tab labels of the layout top bar
$navJs = "(()=>{const vis=e=>e.getClientRects().length>0;const bc=document.querySelector('.el-breadcrumb');const parts=bc?[...bc.querySelectorAll('.el-breadcrumb__item')].map(e=>(e.innerText||'').trim()):[];const tb=[...document.querySelectorAll('.tab-bar .tab-item .tab-label')].filter(vis).map(e=>(e.innerText||'').trim());return JSON.stringify({bc:parts,tabs:tb})})()"
function NavSnap { $raw = EvalJs $navJs; try { return ($raw | ConvertFrom-Json) } catch { return $null } }
function BcText($snap) { if (-not $snap) { return '' }; return (@($snap.bc) -join ' / ') }
function DupTabs($snap) { if (-not $snap) { return @() }; return @(@($snap.tabs) | Group-Object | Where-Object { $_.Count -gt 1 } | ForEach-Object { $_.Name }) }

foreach ($r in $routes) {
  ClearErrs | Out-Null
  Open $r 1600
  $errs = Errs
  $badWord = ''
  foreach ($k in $errKeys) { if ((BodyHas (ZH $k)) -match 'true') { $badWord = $k; break } }
  $rowsObj = Rows 0
  $n = if ($rowsObj -and $rowsObj.PSObject.Properties['n']) { $rowsObj.n } else { '-' }
  $ok = ($errs -match '\[\]' -or $errs -eq '' -or $errs -eq '""') -and $badWord -eq ''

  $snap = NavSnap
  $bc = BcText $snap
  $bcN = @($snap.bc).Count
  $dup = DupTabs $snap
  $bcOk = $bcN -ge 2
  $dupOk = $dup.Count -eq 0

  # locale guard (see the note above $locJs)
  $locRaw = EvalJs $locJs
  $loc = $null; try { $loc = $locRaw | ConvertFrom-Json } catch { }
  if ($loc) {
    $locPg = Dec $loc.pg
    if ($locPg -and ($locPg -notmatch [regex]::Escape($CH_GONG))) { $script:locBad++; Write-Host ('BAD ' + $r + ' locale: pagination is not Chinese -> ' + $locPg) }
    if ($locPg -match 'Total') { $script:locBad++; Write-Host ('BAD ' + $r + ' locale: pagination still shows Total -> ' + $locPg) }
    foreach ($etxt in @($loc.empty)) { $ev = Dec $etxt; if ($ev -eq 'No Data' -or $ev -eq 'No data') { $script:locBad++; Write-Host ('BAD ' + $r + ' locale: table empty state is still English') } }
  }

  # regression class: the same page with a query string must keep the identical breadcrumb
  Open ($r + '?__probe=1') 1400
  $snap2 = NavSnap
  $bc2 = BcText $snap2
  $dup2 = DupTabs $snap2
  $qOk = ($bc -ne '') -and ($bc2 -eq $bc) -and ($dup2.Count -eq 0)

  if (-not $bcOk) { $script:bcBad++ }
  if (-not $dupOk) { $script:dupBad++ }
  if (-not $qOk) { $script:qBad++ }
  if (-not ($ok -and $bcOk -and $dupOk -and $qOk)) {
    $script:bad++
    Write-Host ("BAD " + $r + " errs=" + $errs + " word=" + $badWord + " rows=" + $n + " bc=[" + $bc + "] bcQ=[" + $bc2 + "] dup=" + ($dup -join ',') + " dupQ=" + ($dup2 -join ','))
  } else {
    Write-Host ("OK  " + $r + " rows=" + $n + " bc=" + $bc)
  }
}

Write-Host '--- deep links (query on a menu path) ---'
foreach ($r in $deepLinks) {
  ClearErrs | Out-Null
  Open $r 1700
  $snap = NavSnap
  $bc = BcText $snap
  $bcN = @($snap.bc).Count
  $dup = DupTabs $snap
  $errs = Errs
  $ok = ($bcN -ge 2) -and ($dup.Count -eq 0) -and ($errs -match '\[\]')
  if (-not $ok) { $script:bad++; $script:bcBad++ }
  if ($ok) { Write-Host ("OK  " + $r + " bc=" + $bc) } else { Write-Host ("BAD " + $r + " bc=[" + $bc + "] dup=" + ($dup -join ',') + " errs=" + $errs) }
}

# --- tab memory (new 2026-09-19 behaviour): clicking a top tab must restore the page's LAST url, query included,
#     without creating a second tab (tab identity = query-free path, Tab.fullPath keeps the last visited url).
Write-Host '--- tab memory: a top tab restores the page last URL (query included) ---'
Open '/inventory/return-sort' 3000
$zBills = B64 (ZH 'tab_rs_bills')
EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const K=T('$zBills');const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-tabs__item')].filter(e=>vis(e)&&(e.innerText||'').trim()===K)[0];if(!it)return 'NOITEM';it.click();return 'OK'})()" | Out-Null
Start-Sleep -Milliseconds 1700
$urlBills = EvalJs 'String(location.pathname + location.search)'
Open '/dashboard' 2200
$cntBefore = [int](EvalJs $tabCountJs)
$zSort = B64 (ZH 'menu_sale_sort')
$clickRes = EvalJs "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const K=T('$zSort');const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.tab-bar .tab-item')].filter(e=>vis(e)&&(((e.querySelector('.tab-label')||{}).innerText)||'').trim()===K)[0];if(!it)return 'NOTAB';it.click();return 'OK'})()"
Start-Sleep -Milliseconds 2300
$urlBack = EvalJs 'String(location.pathname + location.search)'
$inner = Dec (EvalJs "(()=>{const B=s=>btoa(unescape(encodeURIComponent(s||'')));const vis=e=>e.getClientRects().length>0;const it=[...document.querySelectorAll('.el-tabs__item')].filter(e=>vis(e)&&e.classList.contains('is-active'))[0];return it?B((it.innerText||'').trim()):''})()")
$snapT = NavSnap
$cntAfter = [int](EvalJs $tabCountJs)
$bcT = @($snapT.bc).Count
$okMem = ($urlBills -eq '/inventory/return-sort?tab=bills') -and ($clickRes -eq 'OK') -and ($urlBack -eq $urlBills) -and ($inner -eq (ZH 'tab_rs_bills')) -and ($bcT -ge 2) -and ($cntAfter -eq $cntBefore)
if (-not $okMem) { $script:bad++; $script:memBad++ }
Write-Host ($(if ($okMem) { 'OK  ' } else { 'BAD ' }) + 'tab-memory click=' + $clickRes + ' billsUrl=' + $urlBills + ' back=' + $urlBack + ' innerTab=' + $inner + ' bc=' + $bcT + ' tabs=' + $cntBefore + '->' + $cntAfter)

# --- tab bar cleanup: the sweep opens one tab per page; reset so the shared test browser does not accumulate
Write-Host '--- tab bar cleanup ---'
EvalJs "localStorage.removeItem('beichen_tabs');'ok'" | Out-Null
Open '/dashboard' 2000
$cntClean = [int](EvalJs $tabCountJs)
if ($cntClean -le 1) { Write-Host ('OK  tab bar reset (' + $cntClean + ' tab)') } else { $script:bad++; $script:memBad++; Write-Host ('BAD tab bar not reset (tabs=' + $cntClean + ')') }

# 2026-09-19: negative control for the error detector. The hook used to be installed once, while every Open
# is a full page load that wipes it - so "bad=0" was only ever measuring the first page. Open() now re-installs
# the hook after each load (ui-e2e-lib.ps1); this self-test proves the detector is really alive on a freshly
# loaded page by firing one request that must fail.
Open '/dashboard' 1500
$hookType = EvalJs "String(typeof window.__errs)"
$st = ErrHookSelfTest
$selfOk = ($st -match 'HTTP[45]|APICODE[45]')
Write-Host ('[SELFTEST] typeof=' + $hookType + ' detector=' + $(if ($selfOk) { 'LIVE' } else { 'DEAD' }) + ' errs=' + $st)
Write-Host ("NAV DONE bad=" + $script:bad + " total=" + $routes.Count + " selftest=" + $(if ($selfOk) { 'PASS' } else { 'FAIL' }) + " bcBad=" + $script:bcBad + " dupTabBad=" + $script:dupBad + " queryVariantBad=" + $script:qBad + " deepLinks=" + $deepLinks.Count + " tabMemoryBad=" + $script:memBad + " localeBad=" + $script:locBad)
