# R3 verification (2026-09-20): silent-failure family.
#
#   -Part 1  sale/exchange list: confirming then CANCELLING the audit dialog must not raise
#            (before the fix the un-caught confirm rejection surfaced as an error) and must not mutate data.
#   -Part 2  purchase/exchange list: same check (the same pattern lived on 3 pages).
#   -Part 3  pages touched for F7-174/F7-184/F7-198/F7-185/F7-192 must still render (regression smoke):
#            inventory/return-sort, inventory/stock-loss, system/menu, system/role, system/user,
#            system/settings, dashboard, template (contract template page).
#
# ASCII ONLY - Chinese literals come from ui-e2e-zh.json via ZH / B64.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }
function ClickRowBtnLike([int]$rowIdx, [string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const bs=[...rs[$rowIdx].querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim().indexOf(t)>=0);if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()"
  return (EvalJs $js)
}
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
# cancel the first visible confirm box, then report (box count after, error list)
function CancelConfirmAndReport() {
  $before = EvalJs "String([...document.querySelectorAll('.el-message-box')].filter(e=>e.getClientRects().length>0).length)"
  EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('.el-message-box button')].filter(vis);if(!bs.length)return 'NOBOX';bs[0].click();return 'OK'})()" | Out-Null
  Start-Sleep -Milliseconds 1500
  $after = EvalJs "String([...document.querySelectorAll('.el-message-box')].filter(e=>e.getClientRects().length>0).length)"
  return ($before + '/' + $after)
}

# ---------------------------------------------------------------- Part 1 / 2
if ($Part -eq 0 -or $Part -eq 1 -or $Part -eq 2) {
  EnsureLogin | Out-Null
  $pages = @()
  if ($Part -eq 0 -or $Part -eq 1) { $pages += @{ name = 'sale-exchange'; path = '/sale/exchange' } }
  if ($Part -eq 0 -or $Part -eq 2) { $pages += @{ name = 'purchase-exchange'; path = '/inventory/purchase-exchange' } }
  foreach ($p in $pages) {
    Step ('F7-144/154 confirm-cancel on ' + $p.name)
    Open $p.path 3500
    Write-Host ('  table ready = ' + (WaitVisible '.el-table'))
    ClearErrs | Out-Null
    $draft = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];const AU=String.fromCharCode(23457,26680);for(let i=0;i<rs.length;i++){const bs=[...rs[i].querySelectorAll('button')];if(bs.some(b=>(b.innerText||'').trim().indexOf(AU)>=0))return String(i)}return '-1'})()"
    Write-Host ('  first row with an audit button = ' + $draft)
    Ok ($draft -ne '-1') 'found a row offering the audit action'
    if ($draft -ne '-1') {
      Write-Host ('  click audit: ' + (ClickRowBtnLike ([int]$draft) (ZH 'btn_audit')))
      Start-Sleep -Milliseconds 1200
      $boxes = CancelConfirmAndReport
      Write-Host ('  boxes before/after cancel = ' + $boxes)
      Ok ($boxes -eq '1/0') 'confirm box appeared and closed on cancel'
      $errs = Errs
      Write-Host ('  errs=' + $errs)
      Ok ($errs -eq '[]') 'no unhandled rejection / API error after cancelling'
    }
    Write-Host ('errs total=' + (Errs))
    Summary ('F7-144/154 ' + $p.name)
  }
}

# ---------------------------------------------------------------- Part 3 smoke
if ($Part -eq 0 -or $Part -eq 3) {
  $paths = @(
    '/inventory/return-sort',
    '/inventory/stock-loss',
    '/system/menu',
    '/system/role',
    '/system/user',
    '/system/settings',
    '/dashboard',
    '/template'
  )
  Step 'smoke: every page touched by R3 still renders'
  foreach ($path in $paths) {
    Open $path 3000
    # NOTE: not every page uses el-card (return-sort's default tab uses el-collapse, settings uses
    # el-tabs, template uses panels) => accept any of the common containers.
    $cards = WaitVisible '.el-card, .el-tabs, .el-form, .el-table, .el-collapse'
    $errs = Errs
    Write-Host ('  ' + $path + ' containers=' + $cards + ' errs=' + $errs)
    Ok (($cards -match '^\d+$') -and ([int]$cards -gt 0)) ($path + ' rendered')
    Ok ($errs -eq '[]') ($path + ' no JS/API errors')
  }
  Summary 'R3 smoke'
}
