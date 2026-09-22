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

Step '1) the detail table renders and the numeric headers carry sort carets'
Open '/analysis/customer' 4200
ClearErrs | Out-Null
Start-Sleep -Milliseconds 2500

$jsHeads = TableJs "JSON.stringify({heads:[...t.querySelectorAll('.el-table__header th')].filter(vis).map(th=>({t:(th.innerText||'').trim().replace(/\s+/g,' '),carets:th.querySelectorAll('.caret-wrapper').length,clipped:th.scrollWidth>th.clientWidth+1})),rows:[...t.querySelectorAll('.el-table__body tbody tr')].filter(vis).length})"
$raw = (EvalJs $jsHeads).Replace('\"', '"')
$m = [regex]::Match($raw, '\{.*\}')
if (-not $m.Success) { Write-Host ('FAIL cannot read table headers: ' + $raw); exit 1 }
$t = $m.Value | ConvertFrom-Json
Ok2 ($t.rows -gt 0) ('detail table has rows (' + $t.rows + ')')
$sortables = @($t.heads | Where-Object { $_.carets -gt 0 })
Write-Host ('  headers = ' + (($t.heads | ForEach-Object { $_.t }) -join ' | '))
Write-Host ('  sortable headers (caret) = ' + $sortables.Count + ' -> ' + (($sortables | ForEach-Object { $_.t }) -join ' | '))
Ok2 ($sortables.Count -eq 9) ('exactly the 9 numeric columns are sortable -- got ' + $sortables.Count)
$clipped = @($sortables | Where-Object { $_.clipped })
Ok2 ($clipped.Count -eq 0) ('no sortable header is clipped by its caret -- offending: ' + (($clipped | ForEach-Object { $_.t }) -join ','))

Step '2) clicking the amount header sorts by VALUE (not lexicographically)'
# column index inside the first visible table: 0 customerCode, 1 customer, 2 amount, ... 7 profitRate, 9 orderCount, 10 unpaid
$base = ToNums (ReadColAt 2)
Write-Host ('  base order sample = ' + (($base | Select-Object -First 5) -join ', '))
$mx = ($base | Measure-Object -Maximum).Maximum
$mn = ($base | Measure-Object -Minimum).Minimum

ClickColAt 2
Start-Sleep -Milliseconds 700
$v1 = ToNums (ReadColAt 2)
Write-Host ('  after 1st click   = ' + (($v1 | Select-Object -First 5) -join ', '))
Ok2 ((Monotonic $v1 $true) -or (Monotonic $v1 $false)) 'first click yields an ordered sequence'
Ok2 (Monotonic $v1 $true) ('first click is ascending (smallest first: ' + $v1[0] + ' vs min ' + $mn + ')')
Ok2 ([Math]::Abs($v1[0] - $mn) -lt 1e-9) 'the minimum value sits at the top after ascending sort'
Ok2 ([Math]::Abs($v1[$v1.Count - 1] - $mx) -lt 1e-9) 'the maximum value sits at the bottom after ascending sort'

Step '3) clicking the same header again flips to descending'
ClickColAt 2
Start-Sleep -Milliseconds 700
$v2 = ToNums (ReadColAt 2)
Write-Host ('  after 2nd click   = ' + (($v2 | Select-Object -First 5) -join ', '))
Ok2 (Monotonic $v2 $false) 'second click yields a descending sequence'
Ok2 ([Math]::Abs($v2[0] - $mx) -lt 1e-9) 'the maximum value now sits at the top (triangle toggled)'

Step '4) a DECIMAL column (profitRate, index 7) proves numeric ordering'
$rates0 = ReadColAt 7
Write-Host ('  rate values (page order) = ' + ($rates0 -join ', '))
ClickColAt 7
Start-Sleep -Milliseconds 700
$rates = ToNums (ReadColAt 7)
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
