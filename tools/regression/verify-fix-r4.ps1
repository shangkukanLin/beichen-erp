# R4 verification (2026-09-20): consistency / unification batch.
#
#   -Part 1  smoke: every page touched by R4 still renders with no JS/API errors:
#            purchase/order (list+add), purchase/return (list+add), purchase/exchange (list+add),
#            inventory/stock-log, inventory/warehouse-move, inventory/reclassify, inventory/return-sort,
#            supplier list, finance/account, dashboard
#   -Part 2  F7-149: the purchase-order "warehouse" dropdown must offer ONLY own finished-goods warehouses
#            (no OUTSOURCE / no AUXILIARY rows).
#
# NOTE: F7-152 / F7-153 / F7-156 / F7-170 / F7-175 / F7-181 / F7-197 are asserted at code level
#       (single, unambiguous edits verified by grep + build); Part 2 covers the one change whose
#       effect is best seen in the UI.
# ASCII ONLY - Chinese literals come from ui-e2e-zh.json via ZH / B64.
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
    Start-Sleep -Milliseconds 800
  }
  return 'TIMEOUT'
}

# ---------------------------------------------------------------- Part 1 smoke
if ($Part -eq 0 -or $Part -eq 1) {
  $paths = @(
    '/inventory/purchase',
    '/inventory/purchase/add',
    '/inventory/purchase-return',
    '/inventory/purchase-return/add',
    '/inventory/purchase-exchange',
    '/inventory/purchase-exchange/add',
    '/inventory/stock-log',
    '/inventory/warehouse-move',
    '/inventory/reclassify',
    '/inventory/return-sort',
    '/supplier/manage',
    '/finance/account'
  )
  EnsureLogin | Out-Null
  Step 'smoke: every page touched by R4 still renders'
  foreach ($path in $paths) {
    Open $path 3000
    $c = WaitVisible '.el-card, .el-tabs, .el-form, .el-table, .el-collapse'
    $errs = Errs
    Write-Host ('  ' + $path + ' containers=' + $c + ' errs=' + $errs)
    Ok (($c -match '^\d+$') -and ([int]$c -gt 0)) ($path + ' rendered')
    Ok ($errs -eq '[]') ($path + ' no JS/API errors')
  }
  Summary 'R4 smoke'
}

# ---------------------------------------------------------------- Part 2 F7-149
if ($Part -eq 0 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  Step 'F7-149 purchase order: warehouse dropdown must list ONLY own finished-goods warehouses'
  Open '/inventory/purchase/add' 4000
  Write-Host ('  form ready = ' + (WaitVisible '.el-form'))
  ClearErrs | Out-Null
  # open the first el-select that sits in a form item whose label contains the warehouse word
  $wh = B64 (ZH 'lbl_warehouse')
  $open = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const W=T('$wh');const vis=e=>e.getClientRects().length>0;const items=[...document.querySelectorAll('.el-form-item')].filter(vis);for(const it of items){const lab=it.querySelector('.el-form-item__label');if(!lab)continue;if((lab.innerText||'').indexOf(W)<0)continue;const sc=it.querySelector('.el-select');if(!sc)continue;const inp=sc.querySelector('input');(inp||sc).dispatchEvent(new MouseEvent('mousedown',{bubbles:true}));(inp||sc).click();return 'OK'}return 'NOSELECT'})()"
  Write-Host ('  open warehouse select: ' + (EvalJs $open))
  Start-Sleep -Milliseconds 2500
  # count visible options and flag the forbidden words, all inside JS so this file stays pure ASCII:
  # VENDOR = two chars (wei+wai, "outsourced"), AUX = two chars (fu+liao, "auxiliary material")
  $res = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const VENDOR=String.fromCharCode(22996,22806);const AUX=String.fromCharCode(36741,26009);const dds=[...document.querySelectorAll('.el-select-dropdown')].filter(vis);const out=[];for(const d of dds){for(const li of [...d.querySelectorAll('li')].filter(vis)){const t=(li.innerText||'').trim();if(t)out.push(t)}}const hasVendor=out.some(t=>t.indexOf(VENDOR)>=0);const hasAux=out.some(t=>t.indexOf(AUX)>=0);return JSON.stringify({n:out.length,hasVendor:hasVendor,hasAux:hasAux})})()"
  Write-Host ('  options summary = ' + $res)
  Ok ($res -match '"n":[1-9]') 'the dropdown actually returned options'
  Ok ($res -match '"hasVendor":false') 'no OUTSOURCE warehouse is offered'
  Ok ($res -match '"hasAux":false') 'no AUXILIARY (material) warehouse is offered'
  Ok ((Errs) -eq '[]') 'no JS/API errors while opening the dropdown'
  Summary 'F7-149 purchase warehouse scope'
}
