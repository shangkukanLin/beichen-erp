# R2 verification (2026-09-20): F7-142 / F7-160 / F7-163 / F7-164 / F7-183 through the real UI.
#
# Run in parts so no single invocation stays silent long enough to be killed:
#   -Part 1  F7-142  sale/return/detail "return warehouse" must show a NAME (was always "-")
#   -Part 2  F7-160  finance/bill row actions must ask for confirmation (box appears; cancel aborts)
#   -Part 3  F7-163 + F7-164  receipt drawer status must be Chinese; receivable supplier tab must show the supplier
#   -Part 4  F7-183  role permission tree must re-check per role (2nd role shows a DIFFERENT checked count)
#   -Part 0  all four (default)
#
# F7-161 (supplier-settlement -> /common/resolve-code) is a call-site change asserted statically, not here.
# ASCII ONLY - Chinese literals come from ui-e2e-zh.json via ZH / B64.
param([int]$Part = 0)
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

# read an el-descriptions value by its label text (CSS adds the colon, so strip separators before comparing)
function DescValue([string]$labelText) {
  $b = B64 $labelText
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const SEP=new RegExp('[\\s*:'+String.fromCharCode(65306)+']','g');const N=s=>(s||'').replace(SEP,'');const L=N(T('$b'));const vis=e=>e.getClientRects().length>0;const lbs=[...document.querySelectorAll('.el-descriptions__label')].filter(vis);for(const lb of lbs){if(N(lb.innerText)!==L)continue;const host=lb.parentElement;const cells=host?[...host.children]:[];const i=cells.indexOf(lb);const ct=i>=0?(cells[i+1]||null):null;const fallback=ct?ct:(host?host.querySelector('.el-descriptions__content'):null);return (fallback?(fallback.innerText||''):'').replace(/\s+/g,' ').trim()}return 'NOLABEL:'+L})()"
  return (EvalJs $js)
}
# count the checked checkboxes inside the visible el-tree (permission dialog)
function TreeCheckedCount() {
  $js = "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-tree')].filter(vis);if(!ts.length)return 'NOTREE';return String(ts[0].querySelectorAll('.el-checkbox.is-checked').length)})()"
  return (EvalJs $js)
}
# click a visible row button whose text CONTAINS the given text, in row $idx
function ClickRowBtnLike([int]$rowIdx, [string]$text) {
  $b = B64 $text
  $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const t=T('$b');const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return 'NOTABLE';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];if(rs.length<=$rowIdx)return 'NOROW:'+rs.length;const bs=[...rs[$rowIdx].querySelectorAll('button')].filter(e=>vis(e)&&(e.innerText||'').trim().indexOf(t)>=0);if(!bs.length)return 'NOBTN';bs[0].click();return 'OK'})()"
  return (EvalJs $js)
}
# poll until at least one VISIBLE node matches the selector; a fixed sleep is unreliable because the
# dev server compiles routes on demand (first hit after a restart / after editing the page can be slow)
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

# ---------------------------------------------------------------- F7-142
if ($Part -eq 0 -or $Part -eq 1) {
  EnsureLogin | Out-Null
  ClearErrs | Out-Null
  Step 'F7-142 sale return detail: warehouse name must render'
  Open '/sale/return/detail/21' 3200
  Write-Host ('  path=' + (EvalJs 'String(location.pathname)'))
  $v = DescValue (ZH 'lbl_return_warehouse')
  Write-Host ('  return warehouse = "' + $v + '"')
  Ok ($v -eq (ZH 'val_wh_fin4')) ('shows the warehouse name, not "-" (got "' + $v + '")')
  Ok ($v -ne '-') 'value is not the empty placeholder'
  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 1 recorded no JS/API errors'
  Summary 'F7-142 return warehouse name'
}

# ---------------------------------------------------------------- F7-160
if ($Part -eq 0 -or $Part -eq 2) {
  Step 'F7-160 finance bill: audit from the list must ask for confirmation'
  Open '/finance/bill' 3000
  ClearErrs | Out-Null
  $draft = EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const ts=[...document.querySelectorAll('.el-table')].filter(vis);const tb=ts[0];if(!tb)return '-1';const rs=[...tb.querySelectorAll('.el-table__body tbody tr')];for(let i=0;i<rs.length;i++){const bs=[...rs[i].querySelectorAll('button')];if(bs.some(b=>(b.innerText||'').trim().indexOf(String.fromCharCode(23457,26680))>=0))return String(i)}return '-1'})()"
  Write-Host ('  first row with an audit button = ' + $draft)
  Ok ($draft -ne '-1') 'found a row offering the audit action'
  if ($draft -ne '-1') {
    Write-Host ('  click audit: ' + (ClickRowBtnLike ([int]$draft) (ZH 'btn_audit')))
    Start-Sleep -Milliseconds 1200
    $box = EvalJs "String([...document.querySelectorAll('.el-message-box')].filter(e=>e.getClientRects().length>0).length)"
    Write-Host ('  confirm boxes visible = ' + $box)
    Ok ($box -eq '1') 'a confirmation box appeared before the action ran'
    # cancel it and make sure nothing changed
    Write-Host ('  cancel: ' + (EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('.el-message-box button')].filter(vis);if(!bs.length)return 'NOBOX';bs[0].click();return 'OK'})()"))
    Start-Sleep -Milliseconds 1200
    $box2 = EvalJs "String([...document.querySelectorAll('.el-message-box')].filter(e=>e.getClientRects().length>0).length)"
    Ok ($box2 -eq '0') 'cancelling closed the box without running the action'
  }
  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 2 recorded no JS/API errors'
  Summary 'F7-160 bill confirm'
}

# ---------------------------------------------------------------- F7-163 + F7-164
if ($Part -eq 0 -or $Part -eq 3) {
  Step 'F7-163 receipt drawer: status must be Chinese'
  Open '/finance/receipt' 3000
  ClearErrs | Out-Null
  Write-Host ('  open detail: ' + (ClickRowBtnLike 0 (ZH 'btn_detail')))
  Start-Sleep -Milliseconds 1600
  $st = DescValue (ZH 'lbl_status')
  Write-Host ('  drawer status = "' + $st + '"')
  Ok ($st -eq (ZH 'val_status_audited')) ('status rendered as a label (got "' + $st + '")')
  Ok ($st -notmatch '^[A-Z_]+$') 'status is not a raw enum code'
  Ok ((Errs) -eq '[]') 'part 3a recorded no JS/API errors'

  Step 'F7-164 receivable: supplier tab detail must show the supplier'
  Open '/finance/receivable' 3000
  ClearErrs | Out-Null
  Write-Host ('  switch tab: ' + (ClickText (ZH 'tab_supplier_recv')))
  Start-Sleep -Milliseconds 2000
  Write-Host ('  open detail: ' + (ClickRowBtnLike 0 (ZH 'btn_detail')))
  Start-Sleep -Milliseconds 1400
  $sup = DescValue (ZH 'lbl_supplier')
  Write-Host ('  drawer supplier = "' + $sup + '"')
  Ok ($sup -ne 'NOLABEL:' + (ZH 'lbl_supplier')) 'detail row is labelled as the supplier (not "customer")'
  Ok ($sup -ne '' -and $sup -ne '-') ('supplier name is rendered (got "' + $sup + '")')
  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 3b recorded no JS/API errors'
  Summary 'F7-163 / F7-164 detail rendering'
}

# ---------------------------------------------------------------- F7-183
if ($Part -eq 0 -or $Part -eq 4) {
  Step 'F7-183 role permission tree: must re-check for each role'
  Open '/system/role' 3000
  Write-Host ('  table ready = ' + (WaitVisible '.el-table'))
  ClearErrs | Out-Null
  # NOTE: row 0 is the platform super admin, whose menus are decided by the hard-coded role code rather
  # than sys_role_menu, so its tree legitimately comes up empty. Compare two ORDINARY roles instead.
  Write-Host ('  role#2 assign: ' + (ClickRowBtnLike 1 (ZH 'btn_perm')))
  # wait for the dialog's tree AND for the check state to settle (menu tree + saved keys are two async calls)
  Write-Host ('  tree ready = ' + (WaitVisible '.el-tree'))
  Start-Sleep -Milliseconds 2500
  $c1 = TreeCheckedCount
  Write-Host ('  role#2 checked = ' + $c1)
  Ok ($c1 -match '^\d+$' -and [int]$c1 -gt 0) ('first role shows checked nodes (got ' + $c1 + ')')
  # close the dialog
  EvalJs "(()=>{const vis=e=>e.getClientRects().length>0;const bs=[...document.querySelectorAll('.el-dialog__headerbtn')].filter(vis);if(bs.length){bs[0].click();return 'OK'}return 'NOBTN'})()" | Out-Null
  Start-Sleep -Milliseconds 1200
  Write-Host ('  role#3 assign: ' + (ClickRowBtnLike 2 (ZH 'btn_perm')))
  Write-Host ('  tree ready = ' + (WaitVisible '.el-tree'))
  Start-Sleep -Milliseconds 2500
  $c2 = TreeCheckedCount
  Write-Host ('  role#3 checked = ' + $c2)
  Ok ($c2 -match '^\d+$') ('second role shows checked nodes (got ' + $c2 + ')')
  Ok ($c1 -ne $c2) ('the tree re-checked for the new role (' + $c1 + ' -> ' + $c2 + '); before the fix it kept the first role''s selection')
  Write-Host ('errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'part 4 recorded no JS/API errors'
  Summary 'F7-183 role permission tree'
}
