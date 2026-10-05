# Customer analysis detail table -- header sorting (2026-09-22 user request:
#   the numeric columns 销售额/退货额/净额/成本/利润/利润率/占比/订单数/应收余额 must be sortable via the
#   header triangle, ascending/descending).
#
# What it proves on the real page (no code inspection):
#   1) the table renders rows and has a sort caret on each of the 9 numeric columns;
#   2) clicking a numeric header sorts by VALUE (not lexicographically) -- asserted by requiring the
#      largest value to sit at the top (descending) / bottom (ascending), and the sequence to stay monotonic;
#   3) clicking the same header again flips the direction;
#   4) a DECIMAL column (利润率) sorts numerically too -- that is where string order and numeric order
#      actually diverge, so it is the strongest evidence that a numeric comparator is in use;
#   5) no header cell is clipped (the caret must not eat the label);
#   6) no JS/API errors.
# ASCII ONLY by design: headers are addressed by COLUMN INDEX, so no Chinese literals are needed.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
EnsureLogin
WatchErrors
function Step($n) { Write-Host ('--- STEP ' + $n) }

$script:fail = 0
function Ok2([bool]$cond, [string]$msg) { if ($cond) { Write-Host ('PASS ' + $msg) } else { Write-Host ('FAIL ' + $msg); $script:fail++ } }

# NOTE: every probe below is built INSIDE the function so the column index is interpolated at call time.
# (A script-scope $js string would freeze the index at definition time -- that bug made step 4 read the
#  wrong column on the first attempt.)
function TableJs([string]$expr) {
  return "(function(){const vis=e=>e.getClientRects().length>0;const t=[...document.querySelectorAll('.el-table')].filter(vis)[0];if(!t)return 'NOTABLE';return " + $expr + "})()"
}
function ReadColAt([int]$idx) {
  $js = TableJs ("JSON.stringify([...t.querySelectorAll('.el-table__body tbody tr')].filter(vis).map(r=>(((r.querySelectorAll('td')[" + $idx + "]||{}).innerText)||'').trim()))")
  $r = (EvalJs $js).Replace('\"', '"')
  $mm = [regex]::Match($r, '\[.*\]')
  if (-not $mm.Success) { return @() }
  return @(($mm.Value | ConvertFrom-Json))
}
function ClickColAt([int]$idx) {
  EvalJs (TableJs ("(function(){const th=[...t.querySelectorAll('.el-table__header th')].filter(vis)[" + $idx + "];if(!th)return 'NOCELL';th.click();return 'ok'})()")) | Out-Null
}
function ToNums($texts) {
  return @($texts | ForEach-Object { $s = ($_ -replace '[^0-9\.\-]', ''); if ($s -eq '' -or $s -eq '-' -or $s -eq '.') { 0.0 } else { [double]$s } })
}
function Monotonic([double[]]$v, [bool]$asc) {
  for ($i = 1; $i -lt $v.Count; $i++) {
    if ($asc) { if ($v[$i] -lt $v[$i - 1] - 1e-9) { return $false } }
    else { if ($v[$i] -gt $v[$i - 1] + 1e-9) { return $false } }
  }
  return $true
}

Step '1) the detail table renders; every header shows in full; the 9 numeric headers carry carets'
Open '/analysis/customer' 4200
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2500

# 'clipped' is measured on the header cell's inner .cell (Element Plus ellipsises there).
$jsHeads = TableJs "JSON.stringify({heads:[...t.querySelectorAll('.el-table__header th')].filter(vis).map(th=>{const c=th.querySelector('.cell');return {t:(th.innerText||'').trim().replace(/\s+/g,' '),carets:th.querySelectorAll('.caret-wrapper').length,clipped:!!c&&(c.scrollWidth>c.clientWidth+1)}}),rows:[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis).length})"
$raw = (EvalJs $jsHeads).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if (-not $m.Success) { Write-Host ('FAIL cannot read table headers: ' + $raw); exit 1 }
$t = $m.Value | ConvertFrom-Json
Ok2 ($t.rows -gt 0) ('detail table has rows (' + $t.rows + ')')
$sortables = @($t.heads | Where-Object { $_.carets -gt 0 })
Write-Host ('  headers = ' + (($t.heads | ForEach-Object { $_.t }) -join ' | '))
Write-Host ('  sortable headers (caret) = ' + $sortables.Count + ' -> ' + (($sortables | ForEach-Object { $_.t }) -join ' | '))
Ok2 ($sortables.Count -eq 9) ('exactly the 9 numeric columns are sortable -- got ' + $sortables.Count)
# 2026-09-22 (user request): the 客户编码 column was dropped because narrow columns clipped some headers.
# Therefore EVERY header -- sortable or not -- must render its label in full.
$clipped = @($t.heads | Where-Object { $_.clipped })
Ok2 ($clipped.Count -eq 0) ('no header label is clipped (all shown in full) -- offending: ' + (($clipped | ForEach-Object { $_.t }) -join ' , '))
# The 客户编码 column must be gone (asserted by column count + the fact that the first column is 客户).
Ok2 ($t.heads.Count -eq 12) ('the table has 12 columns after dropping the code column -- got ' + $t.heads.Count)
# Removing a column / widening others must not introduce horizontal scrolling (the page's own
# invariant, stated in its comment): total header width must fit the scroll container.
$jsFit = TableJs "(()=>{const w=[...t.querySelectorAll('.el-table__header colgroup col')].reduce((s,c)=>s+(parseFloat(c.getAttribute('width'))||0),0);const box=t.querySelector('.el-scrollbar__wrap')||t;return Math.round(w)+'|'+Math.round(box.clientWidth)})()"
$fit = (EvalJs $jsFit).Trim([char]34) -split '\|'
Write-Host ('  header total width = ' + $fit[0] + 'px, container = ' + $fit[1] + 'px')
Ok2 ($fit.Count -ge 2 -and [int]$fit[0] -le ([int]$fit[1] + 2)) ('columns still fit the container (no horizontal scroll): ' + $fit[0] + ' <= ' + $fit[1])

# 2026-10-05 F7-292 note: the caret positions ARE derived (so the sortable COLUMN ORDER is flexible), but the
# two counts above (9 sortable / 12 total) are deliberate CONTRACTS of this page, not derived values -- an
# intentional column change must update them here. (Before this note the comment claimed the test survives
# add/remove, which contradicted `-eq 12`.)
# Column indices are DERIVED from the caret positions so this test survives column-reorder
# (sortable order is: amount, returnAmount, netAmount, cost, profit, profitRate, share, orderCount, unpaid).
$colsWithCaret = @()
for ($i = 0; $i -lt $t.heads.Count; $i++) { if ($t.heads[$i].carets -gt 0) { $colsWithCaret += $i } }
$AMT = $colsWithCaret[0]
$RATE = $colsWithCaret[5]
Write-Host ('  column indices from carets: amount=' + $AMT + ' profitRate=' + $RATE + ' (of ' + $colsWithCaret.Count + ' sortable)')

# The sort indicator must be TWO triangles stacked VERTICALLY -- up (ascending) above, down (descending)
# below -- both fully inside the wrapper and not overlapping (2026-09-22 user request). Element Plus's
# markup already carries both; the risk is the shrink of .caret-wrapper: its default is 24x34 and the
# lower caret is positioned with bottom:7px, so shrinking only the width makes it poke out and overlap.
$jsCaret = TableJs "JSON.stringify([...t.querySelectorAll('.el-table__header th')].filter(vis).filter(th=>th.querySelector('.caret-wrapper')).map(th=>{const w=th.querySelector('.caret-wrapper'),wr=w.getBoundingClientRect();const c=[...w.querySelectorAll('.sort-caret')].filter(x=>x.getClientRects().length>0);let inside=true;c.forEach(x=>{const r=x.getBoundingClientRect();if(r.top<wr.top-1||r.bottom>wr.bottom+1||r.left<wr.left-1||r.right>wr.right+1)inside=false});const up=w.querySelector('.sort-caret.ascending'),dn=w.querySelector('.sort-caret.descending');const ur=up.getBoundingClientRect(),dr=dn?dn.getBoundingClientRect():null;return {n:c.length,inside:inside,upAbove:dr?(ur.bottom<=dr.top+1):false,sameLeft:dr?(Math.abs(ur.left-dr.left)<=1):false,w:Math.round(wr.width),h:Math.round(wr.height)}}))"
$rawC = (EvalJs $jsCaret).Replace('\"', '"')
$mc = [regex]::Match($rawC, '\[.*\]')
if (-not $mc.Success) { Write-Host ('FAIL cannot read carets: ' + $rawC); exit 1 }
$cares = @($mc.Value | ConvertFrom-Json)
$notTwo = @($cares | Where-Object { $_.n -ne 2 })
$outBox = @($cares | Where-Object { -not $_.inside })
$badStack = @($cares | Where-Object { -not ($_.upAbove -and $_.sameLeft) })
Write-Host ('  carets visible per header: ' + ((@($cares.n)) -join ',') + '   wrapper=' + (@($cares.w)[0]) + 'x' + (@($cares.h)[0]))
Ok2 ($notTwo.Count -eq 0) ('exactly TWO carets are shown per header (up + down) -- offenders: ' + $notTwo.Count)
Ok2 ($outBox.Count -eq 0) ('no caret pokes outside its wrapper -- offenders: ' + $outBox.Count)
Ok2 ($badStack.Count -eq 0) ('the up caret sits ABOVE the down caret, horizontally aligned, not overlapping -- offenders: ' + $badStack.Count)

Step '2) clicking the amount header sorts by VALUE (not lexicographically)'
$base = ToNums (ReadColAt $AMT)
Write-Host ('  base order sample = ' + (($base | Select-Object -First 5) -join ', '))
$mx = ($base | Measure-Object -Maximum).Maximum
$mn = ($base | Measure-Object -Minimum).Minimum

ClickColAt $AMT
Start-Sleep -Milliseconds 700
$v1 = ToNums (ReadColAt $AMT)
Write-Host ('  after 1st click   = ' + (($v1 | Select-Object -First 5) -join ', '))
Ok2 ((Monotonic $v1 $true) -or (Monotonic $v1 $false)) 'first click yields an ordered sequence'
Ok2 (Monotonic $v1 $true) ('first click is ascending (smallest first: ' + $v1[0] + ' vs min ' + $mn + ')')
Ok2 ([Math]::Abs($v1[0] - $mn) -lt 1e-9) 'the minimum value sits at the top after ascending sort'
Ok2 ([Math]::Abs($v1[$v1.Count - 1] - $mx) -lt 1e-9) 'the maximum value sits at the bottom after ascending sort'

Step '3) clicking the same header again flips to descending'
ClickColAt $AMT
Start-Sleep -Milliseconds 700
$v2 = ToNums (ReadColAt $AMT)
Write-Host ('  after 2nd click   = ' + (($v2 | Select-Object -First 5) -join ', '))
Ok2 (Monotonic $v2 $false) 'second click yields a descending sequence'
Ok2 ([Math]::Abs($v2[0] - $mx) -lt 1e-9) 'the maximum value now sits at the top (triangle toggled)'

Step '4) a DECIMAL column (profitRate) proves numeric ordering'
$rates0 = ReadColAt $RATE
Write-Host ('  rate values (page order) = ' + ($rates0 -join ', '))
ClickColAt $RATE
Start-Sleep -Milliseconds 700
$rates = ToNums (ReadColAt $RATE)
Write-Host ('  rate order sample = ' + (($rates | Select-Object -First 5) -join ', '))
$rmax = ($rates | Measure-Object -Maximum).Maximum
$rmin = ($rates | Measure-Object -Minimum).Minimum
Ok2 ($rates.Count -gt 0) ('read the profitRate column (' + $rates.Count + ' values)')
Ok2 (Monotonic $rates $true) 'the decimal column sorts ascending by value'
Ok2 ([Math]::Abs($rates[0] - $rmin) -lt 1e-9) ('the numeric minimum sits first (' + $rates[0] + ' vs min ' + $rmin + ')')
# informational: on this data set, does string order differ from numeric order? (it must, for the
# assertion above to really discriminate a numeric comparator from the default lexical one)
$lexKeys = @($rates0 | Sort-Object)
$numKeys = @(ToNums $rates0 | Sort-Object | ForEach-Object { $_.ToString() })
Write-Host ('  (info) lexical order differs from numeric order here: ' + (($lexKeys -join ',') -ne ($numKeys -join ',')))

Write-Host ('  errs=' + (Errs))
Ok2 ((Errs) -eq '[]') 'no JS/API errors on the customer analysis page'

if ($script:fail -eq 0) { Write-Host 'RESULT PASS customer detail table header sorting' } else { Write-Host ('RESULT FAIL count ' + $script:fail); exit 1 }
