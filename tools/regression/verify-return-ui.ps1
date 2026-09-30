# verify-return-ui.ps1 -- the two RETURN list pages must follow ONE list-page house style.
#
#   2026-09-21 (user: "the outsource-return and material-return UI needs optimising and unifying"),
#   covers the A/B/C items that landed that day:
#     A) material-return used to scroll sideways on BOTH tabs (measured 1055px / 1265px vs a 948px body);
#        outsource-return's ledger carried a literal "**" from an un-rendered markdown bold in its hint.
#     B) one card per page (tabs -> filter row -> table -> pagination), the create button sits at the RIGHT of
#        the filter row and switches with the tab, action set/order and column widths unified across both pages.
#     C) tab wording unified: material-return tabs are now [material-return | repair-return] (was
#        [refund | repair-refund]); the amount column is renamed to "return amount"; the merged tracking
#        column is "sent/returned" and the separate "return progress" column is GONE -- it is a FILTER now,
#        while "closed" is shown inside the status column (same as the outsource page).
#
# PURE ASCII on purpose (PS 5.1 mangles UTF-8 files without a BOM): every Chinese expectation lives inside the
# evaluated JS as a \uXXXX escape, so PowerShell only ever compares booleans.
# READ-ONLY. Rerunnable.

. (Join-Path $PSScriptRoot 'ab-bounded.ps1')
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:5173'
$fail = 0

function EvalJs($js) { return (((agent-browser eval $js) -join "`n").Trim()) }
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Unescape($s) { return ([regex]::Match($s.Replace('\"', '"'), '\{.*\}')).Value | ConvertFrom-Json }

function EnsureOn([string]$url) {
  for ($i = 1; $i -le 3; $i++) {
    agent-browser open $url | Out-Null
    agent-browser wait 3000 | Out-Null
    if ((EvalJs "String(location.pathname)") -notmatch '/login') { return $true }
    Write-Host ('(login page, attempt ' + $i + ')')
    $snap = (agent-browser snapshot -i) -join "`n"
    $refs = [regex]::Matches($snap, 'textbox[^\n]*ref=(e\d+)')
    $btn = [regex]::Match($snap, 'button[^\n]*ref=(e\d+)')
    if ($refs.Count -lt 2 -or -not $btn.Success) { Write-Host '(cannot find login controls)'; return $false }
    agent-browser fill ("@" + $refs[0].Groups[1].Value) 'lin' | Out-Null
    agent-browser fill ("@" + $refs[1].Groups[1].Value) '123' | Out-Null
    agent-browser click ("@" + $btn.Groups[1].Value) | Out-Null
    agent-browser wait 4000 | Out-Null
  }
  return ((EvalJs "String(location.pathname)") -notmatch '/login')
}

# measure one page (+ optional 2nd tab): widths, stickiness of the create button, column names, stray "**"
# NOTE: must NOT be called "Measure" -- that name is a built-in alias for Measure-Object.
function GetRcUi([string]$expectNew, [string]$expectCol, [string]$expectTab2) {
  $js = @"
(()=>{const vis=e=>e.getClientRects().length>0;
 const tabs=[...document.querySelectorAll('.el-tabs__item')].map(x=>(x.innerText||'').trim());
 const btns=[...document.querySelectorAll('button')].filter(vis).map(x=>(x.innerText||'').trim());
 const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];
 let cols=[],sum=0,box=0,sc=false,over=false,rows=-1;
 if(t){const ths=[...t.querySelectorAll('.el-table__header th')];
  cols=ths.map(x=>(x.innerText||'').replace(/\s+/g,'').trim());
  sum=ths.reduce((s,x)=>s+x.offsetWidth,0);box=t.clientWidth;
  sc=t.classList.contains('el-table--scrollable-x');
  const w=t.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');over=w?(w.scrollWidth>w.clientWidth):false;
  rows=t.querySelectorAll('.el-table__body tbody tr').length;}
 const txt=document.body.innerText||'';
 return JSON.stringify({
   tabs:tabs, cols:cols, rows:rows, sum:sum, box:box,
   noScrollX:(!sc && !over),
   noStar:(txt.indexOf('**')<0),
   hasNew:(btns.indexOf('$expectNew')>=0),
   hasCol:(cols.indexOf('$expectCol')>=0),
   hasTab2:(tabs.indexOf('$expectTab2')>=0)
 });})()
"@
  return (Unescape (EvalJs $js))
}

function SwitchTab([int]$idx) {
  EvalJs "(()=>{const it=[...document.querySelectorAll('.el-tabs__item')].filter(x=>x.getClientRects().length>0);if(it.length<=$idx)return 'NOTAB';it[$idx].click();return 'OK'})()" | Out-Null
  agent-browser wait 2200
}

# ------------------------------------------------------------------ outsource return (ledger / repair tabs)
Ok (EnsureOn "$base/outsource/return-order") 'opened /outsource/return-order'
$m1 = GetRcUi ([char]0x65B0 + [char]0x589E + [char]0x65E0 + [char]0x5355 + [char]0x52A0 + [char]0x5DE5 + [char]0x9000 + [char]0x8D27) `
  ([char]0x5173 + [char]0x8054 + [char]0x52A0 + [char]0x5DE5 + [char]0x5355) ([char]0x7EF4 + [char]0x4FEE + [char]0x9000 + [char]0x8D27)
Write-Host ('tab1 head = ' + ($m1.cols -join '/') + '   cols=' + $m1.sum + '/' + $m1.box + ' rows=' + $m1.rows)
Ok ($m1.tabs.Count -eq 2) 'outsource-return has two tabs'
Ok ($m1.hasTab2) 'outsource-return tab2 is the repair-return tab'
Ok ($m1.noScrollX) 'outsource-return / ledger tab: no horizontal scrolling'
Ok ($m1.noStar) 'outsource-return hint has no literal ** left'
Ok ($m1.hasNew) 'outsource-return ledger offers the create button in the filter row'
Ok ($m1.hasCol) 'outsource-return ledger keeps the linked-work-order column'
SwitchTab 1
$m2 = GetRcUi ([char]0x65B0 + [char]0x589E + [char]0x7EF4 + [char]0x4FEE + [char]0x9000 + [char]0x8D27) `
  ([char]0x9001 + [char]0x4FEE + '/' + [char]0x5DF2 + [char]0x8FD4 + [char]0x56DE) ([char]0x52A0 + [char]0x5DE5 + [char]0x9000 + [char]0x8D27)
Write-Host ('tab2 head = ' + ($m2.cols -join '/') + '   cols=' + $m2.sum + '/' + $m2.box + ' rows=' + $m2.rows)
Ok ($m2.noScrollX) 'outsource-return / repair tab: no horizontal scrolling'
Ok ($m2.hasNew) 'repair tab offers the repair create button (it switches with the tab)'
Ok ($m2.hasCol) 'repair tab shows the merged sent/returned column'

# ------------------------------------------------------------------ material return (material / repair tabs)
Ok (EnsureOn "$base/outsource/material-return") 'opened /outsource/material-return'
$m3 = GetRcUi ([char]0x65B0 + [char]0x589E + [char]0x7269 + [char]0x6599 + [char]0x9000 + [char]0x8D27) `
  ([char]0x9000 + [char]0x8D27 + [char]0x91D1 + [char]0x989D) ([char]0x7269 + [char]0x6599 + [char]0x9000 + [char]0x8D27)
Write-Host ('tab1 head = ' + ($m3.cols -join '/') + '   cols=' + $m3.sum + '/' + $m3.box + ' rows=' + $m3.rows)
Ok ($m3.tabs.Count -eq 2) 'material-return has two tabs'
Ok ($m3.hasTab2) 'material-return tab1 is named material-return tab (renamed from refund)'
Ok ($m3.noScrollX) 'material-return / refund tab: no horizontal scrolling (was 1055px vs 948px)'
Ok ($m3.hasNew) 'material-return tab offers its own create button'
Ok ($m3.hasCol) 'the amount column is renamed to return-amount'
SwitchTab 1
$m4 = GetRcUi ([char]0x65B0 + [char]0x589E + [char]0x7EF4 + [char]0x4FEE + [char]0x9000 + [char]0x8D27) `
  ([char]0x9001 + [char]0x4FEE + '/' + [char]0x5DF2 + [char]0x8FD4 + [char]0x56DE) ([char]0x7EF4 + [char]0x4FEE + [char]0x9000 + [char]0x8D27)
Write-Host ('tab2 head = ' + ($m4.cols -join '/') + '   cols=' + $m4.sum + '/' + $m4.box + ' rows=' + $m4.rows)
Ok ($m4.hasTab2) 'material-return tab2 is named repair-return (renamed from repair-refund)'
Ok ($m4.noScrollX) 'material-return / repair tab: no horizontal scrolling (was 1265px vs 963px)'
Ok ($m4.hasNew) 'repair tab offers its own create button'
Ok ($m4.hasCol) 'repair tab shows the merged sent/returned column'

if ($fail -eq 0) { Write-Output 'RESULT PASS both return pages share one list-page style (single card / create button in the filter row / no horizontal scroll / same wording and columns)' } else { Write-Output ('RESULT FAIL count=' + $fail); exit 1 }
