# P17 (2026-09-23 user request): unified secondary-page template -- verified on the PILOT page.
# Pilot = /inventory/sale/add (the user reviews the shell on this page before we roll it out).
# What is asserted:
#   1) the unified shell renders: page header (title + top-right back) + bottom action bar; the old
#      "back to list" wording is gone;
#   2) CLEAN page  : back closes the CURRENT TAB and lands on the list page;
#   3) DIRTY page  : back asks first -- "keep editing" stays on the page, "confirm" leaves;
#   4) DIRTY page  : closing the tab (x) is guarded exactly the same way;
#   5) DIRTY page  : switching to another sidebar menu is guarded too (route-level guard).
# ASCII ONLY (BOM guard): every Chinese label comes from ui-e2e-zh.json via ZH/B64.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors

$PILOT = '/inventory/sale/add'
$LIST = '/inventory/sale'

$BACK = ZH 'btn_back'
$SAVE = ZH 'btn_save'
$LEAVE_OK = ZH 'btn_leave_ok'
$LEAVE_STAY = ZH 'btn_leave_stay'
$MSG = ZH 'msg_unsaved'
$BACK_LIST = ZH 'txt_back_list'
$ADD_TITLE = ZH 'lbl_sale_add_new'
$MENU_HOME = ZH 'menu_home'

function Step($n) { Write-Host ('--- STEP ' + $n) }
$script:fail = 0
function Ok2([bool]$cond, [string]$msg) { if ($cond) { Write-Host ('PASS ' + $msg) } else { Write-Host ('FAIL ' + $msg); $script:fail++ } }

function PathNow() { return (EvalJs 'String(location.pathname)') }

function TabLabels() {
  $raw = (EvalJs "(()=>JSON.stringify([...document.querySelectorAll('.tab-item .tab-label')].map(e=>(e.innerText||'').trim())))()").Replace('\"', '"')
  try { return @($raw | ConvertFrom-Json) } catch { return @() }
}

# count buttons with an EXACT label inside a scope, so "the back button lives in the header" is provable
function CountBtnIn([string]$text, [string]$sel) {
  $b = B64 $text
  $js = "(()=>{const T=b=>new TextDecoder().decode(Uint8Array.from(atob(b),c=>c.charCodeAt(0)));const t=T('$b');const r=document.querySelector('$sel');if(!r)return 'NOSCOPE';return String([...r.querySelectorAll('button')].filter(e=>e.getClientRects().length>0&&(e.innerText||'').trim()===t).length)})()"
  # eval output can carry surrounding whitespace/newlines -> strip it before comparing
  return ((EvalJs $js) -replace '\s', '')
}

function ClickActiveTabClose() {
  return (EvalJs "(()=>{const t=document.querySelector('.tab-item.active .tab-close');if(!t)return 'NOCLOSE';t.click();return 'OK'})()")
}

# typing in the last field (remark) is enough to make the form dirty
function MakeDirty() {
  $r = FillLabel 'lbl_remark' ('P17-' + (Get-Random))
  Start-Sleep -Milliseconds 800
  return $r
}

Step '1) the pilot page renders the unified shell'
Open $PILOT 3200
ClearErrs | Out-Null
$title = Txt '.page-header__title'
Ok2 ($title -ne 'NOEL' -and $title.Length -gt 0) ('page header shows a title: ' + $title)
Write-Host ('back buttons in header = ' + (CountBtnIn $BACK '.page-header__ops') + ' / header ops exists = ' + (EvalJs "String(!!document.querySelector('.page-header__ops'))"))
Ok2 ((CountBtnIn $BACK '.page-header__ops') -eq '1') 'exactly one back button, inside the header action area'
Ok2 ((CountBtnIn $SAVE '.page-header__leading') -eq '1') 'the primary action (save) sits in the header, left of the title'
# and it really IS left of the title (DOM order inside the header)
$order = EvalJs "(()=>{const h=document.querySelector('.page-header');if(!h)return 'NOHDR';return [...h.querySelectorAll('.page-header__leading,.page-header__title')].map(e=>(e.className||'').indexOf('leading')>=0?'leading':'title').join('>')})()"
Ok2 ($order -eq 'leading>title') ('the save block comes before the title in the header (' + $order + ')')
Ok2 ((BodyHas $BACK_LIST) -eq 'False') 'the old "back to list" wording is gone'

Step '2) CLEAN page: back closes the current tab and returns to the list'
Open $LIST 2600
Open $PILOT 3200
Write-Host ('tabs before = ' + ((TabLabels) -join ' | '))
ClickBtn 'btn_back' '.page-header__ops' | Out-Null
Start-Sleep -Milliseconds 1800
Ok2 ((PathNow) -eq $LIST) ('back landed on the list (' + (PathNow) + ')')
Ok2 (((TabLabels) -notcontains $ADD_TITLE)) 'the pilot tab was closed (no leftover tab)'

Step '3) DIRTY page: back asks first, "keep editing" stays, "confirm" leaves'
Open $PILOT 3200
Write-Host ('make dirty: ' + (MakeDirty))
ClickBtn 'btn_back' '.page-header__ops' | Out-Null
Start-Sleep -Milliseconds 1200
Ok2 ((BodyHas $MSG) -eq 'True') 'back on a dirty page shows the unsaved-changes prompt'
ClickDialogBtn 'btn_leave_stay' 1200 | Out-Null
Start-Sleep -Milliseconds 1000
Ok2 ((PathNow) -eq $PILOT) ('"keep editing" stays on the page (' + (PathNow) + ')')
ClickBtn 'btn_back' '.page-header__ops' | Out-Null
Start-Sleep -Milliseconds 1200
ClickDialogBtn 'btn_leave_ok' 1400 | Out-Null
Start-Sleep -Milliseconds 1800
Ok2 ((PathNow) -eq $LIST) ('"confirm" leaves for the list (' + (PathNow) + ')')

Step '4) DIRTY page: closing the tab (x) is guarded the same way'
Open $PILOT 3200
MakeDirty | Out-Null
ClickActiveTabClose | Out-Null
Start-Sleep -Milliseconds 1200
Ok2 ((BodyHas $MSG) -eq 'True') 'closing the tab of a dirty page asks first'
ClickDialogBtn 'btn_leave_stay' 1200 | Out-Null
Start-Sleep -Milliseconds 1000
Ok2 ((PathNow) -eq $PILOT) 'cancelled tab close keeps the page open'

Step '5) DIRTY page: switching to another sidebar menu is guarded too'
ClickText $MENU_HOME | Out-Null
Start-Sleep -Milliseconds 1200
Ok2 ((BodyHas $MSG) -eq 'True') 'switching menu away from a dirty page asks first'
ClickDialogBtn 'btn_leave_ok' 1400 | Out-Null
Start-Sleep -Milliseconds 1800
Ok2 ((PathNow) -eq '/dashboard') ('confirm leaves for the clicked menu (' + (PathNow) + ')')

Ok2 (((Errs) -eq '[]')) ('no JS/API errors during the whole flow (' + (Errs) + ')')

if ($script:fail -eq 0) { Write-Host 'RESULT PASS unified secondary-page shell + guarded back (pilot /inventory/sale/add)' }
else { Write-Host ('RESULT FAIL count ' + $script:fail); exit 1 }
