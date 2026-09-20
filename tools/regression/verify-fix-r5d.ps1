# R5d verification (2026-09-20): the last batch of report F7 items.
#   F7-145 sale/return/add  fetchSaleOrders now filters by keyword
#   F7-148 sale order/return/exchange detail  documented keep-alive dependency
#   F7-157 purchase add/detail  routed through @/api/purchase
#   F7-166 payable comment   F7-168 payable-transfer onMounted   F7-169 upload button DOM
#   F7-179 reclassify detail duplicate /product request
#   F7-186 company status editable   F7-187 menu parent must not be self/descendant
#   F7-191 StockTakePanel DocStatus + onActivated + size-change   F7-200 403 home target
#
# Runtime assertions are targeted where they are deterministic (403 redirect, company dialog field,
# stock-take panel). F7-145/157/166/169/179/187 are covered by full build + code review and only
# smoke-tested here -- saying so explicitly beats pretending we proved them at runtime.
#
# ASCII ONLY.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function PathNow() { return (EvalJs 'String(location.pathname)') }
function Skip($msg) { Write-Host ('SKIP ' + $msg) }
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

# ---- Part 1: smoke every touched page ----
$pages = @(
  '/finance/payable',
  '/finance/payable-transfer/add',
  '/inventory/reclassify/detail/187',
  '/inventory/stock-take',
  '/outsource/material-stock-take',
  '/sale/return/add',
  '/inventory/sale/detail/281',
  '/sale/return/detail/31',
  '/sale/exchange/detail/17',
  '/inventory/purchase/add',
  '/inventory/purchase-return/add',
  '/inventory/purchase-return/detail/12',
  '/system/menu'
)

if ($Part -eq 0 -or $Part -eq 1) {
  Step 'R5d smoke: touched pages'
  EnsureLogin | Out-Null
  foreach ($p in $pages) {
    Open $p 3000
    $c = WaitVisible '.el-card, .el-tabs, .el-form, .el-table, .el-descriptions'
    $errs = Errs
    Write-Host ('  ' + $p + ' containers=' + $c + ' path=' + (PathNow) + ' errs=' + $errs)
    if ((PathNow) -eq '/403') { Skip ($p + ' landed on /403 (not mounted for this account)') }
    else {
      Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($p + ' rendered')
      Ok ($errs -eq '[]') ($p + ' no JS/API errors')
    }
  }
  Summary 'R5d smoke'
}

# ---- Part 2: F7-200  403 page must not bounce back to 403 ----
if ($Part -eq 0 -or $Part -eq 2) {
  Step 'F7-200: /403 "return home" target'
  EnsureLogin | Out-Null
  Open '/403' 2500
  Write-Host ('  landed = ' + (PathNow))
  $btn = WaitVisible '.forbidden button'
  Write-Host ('  button visible = ' + $btn)
  Ok (($btn -match '^\d+$') -and ([int]$btn -gt 0)) '403 page shows the home button'
  if (($btn -match '^\d+$') -and ([int]$btn -gt 0)) {
    EvalJs "(()=>{const b=[...document.querySelectorAll('.forbidden button')].filter(e=>e.getClientRects().length>0)[0];b.click();return 'ok'})()" | Out-Null
    Start-Sleep -Milliseconds 2500
    $now = PathNow
    Write-Host ('  after click = ' + $now)
    Ok ($now -ne '/403') ('403 home button leaves /403 (now ' + $now + ')')
  }
  Summary 'F7-200'
}

# ---- Part 3: F7-186 company dialog now exposes 状态 ----
if ($Part -eq 0 -or $Part -eq 3) {
  Step 'F7-186: /company-manage edit dialog has name + status'
  EnsureLogin | Out-Null
  Open '/company-manage' 3000
  if ((PathNow) -eq '/403') { Skip '/company-manage landed on /403 (route not whitelisted for this account)' }
  else {
    $rows = EvalJs "String([...document.querySelectorAll('.el-table__body tr')].filter(e=>e.getClientRects().length>0).length)"
    Write-Host ('  rows = ' + $rows)
    Ok (($rows -match '^\d+$') -and ([int]$rows -gt 0)) 'company list rendered rows'
    # click the first visible 编辑 button
    EvalJs "(()=>{const b=[...document.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()==='编辑')[0];if(b)b.click();return b?'ok':'none'})()" | Out-Null
    Start-Sleep -Milliseconds 1200
    $items = EvalJs "(()=>{const d=[...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).pop();if(!d)return '0';return String(d.querySelectorAll('.el-form-item').length)})()"
    $hasSelect = EvalJs "(()=>{const d=[...document.querySelectorAll('.el-dialog')].filter(e=>e.getClientRects().length>0).pop();if(!d)return 'no';return d.querySelector('.el-select')?'yes':'no'})()"
    Write-Host ('  dialog form-items = ' + $items + ' hasSelect=' + $hasSelect)
    Ok ([int]$items -ge 2) 'edit dialog exposes more than just the name field'
    Ok ($hasSelect -eq 'yes') 'edit dialog contains the status select (F7-186)'
  }
  Summary 'F7-186'
}

# ---- Part 4: F7-191 stock-take panel ----
if ($Part -eq 0 -or $Part -eq 4) {
  Step 'F7-191: stock-take panel renders, status filter + action buttons intact'
  EnsureLogin | Out-Null
  foreach ($p in @('/inventory/stock-take', '/outsource/material-stock-take')) {
    Open $p 3200
    $c = WaitVisible '.el-table'
    $errs = Errs
    # 状态筛选下拉应有多个 DocStatus 选项（点开数一数）
    $statusSel = EvalJs "(()=>{const s=[...document.querySelectorAll('.query-card .el-select')].filter(e=>e.getClientRects().length>0).pop();if(!s)return 'none';s.querySelector('.el-select__wrapper')?.click();return 'ok'})()"
    Start-Sleep -Milliseconds 900
    $opts = EvalJs "String([...document.querySelectorAll('.el-select-dropdown__item')].filter(e=>e.getClientRects().length>0).length)"
    EvalJs "(()=>{document.body.click();return 'ok'})()" | Out-Null
    Start-Sleep -Milliseconds 400
    Write-Host ('  ' + $p + ' table=' + $c + ' statusSel=' + $statusSel + ' statusOpts=' + $opts + ' errs=' + $errs)
    Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($p + ' rendered the table')
    Ok ([int]$opts -ge 2) ($p + ' status filter offers DocStatus options')
    Ok ($errs -eq '[]') ($p + ' no JS/API errors')
  }
  Summary 'F7-191'
}
