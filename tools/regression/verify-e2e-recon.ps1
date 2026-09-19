# P9 (2026-09-18 full-flow E2E): reconciliation + retained-data manifest + "only grows, never shrinks" proof.
#   1) 成品 库存 x 流水   2) 物料 库存 x 流水   3) 应收   4) 应付   5) 盘点一致性
#   6) 留存清单（各业务表行数） 7) 与基线 e2e-baseline-20260918.txt 对比（只增不删）
# READ-ONLY. ASCII ONLY.
. (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return @((@($o) | Select-Object -Skip 1) | ForEach-Object { "$_" } | Where-Object { $_ -ne '' })
}
function SqlOne([string]$q) {
  $l = @(SqlLines $q)
  if ($l.Count -lt 1) { return '' }
  return (($l[0] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function Step($n) { Write-Host ('--- STEP ' + $n) }
$pass = 0; $fail = 0
function Ok([bool]$cond, [string]$msg) {
  if ($cond) { $script:pass++; Write-Host ('PASS ' + $msg) } else { $script:fail++; Write-Host ('FAIL ' + $msg) }
}
function Info([string]$msg) { Write-Host ('INFO ' + $msg) }

# ---------------------------------------------------------------- 1) 成品 stock vs log
Step '1) finished goods: warehouse_stock vs warehouse_stock_log (per warehouse)'
$sMap = @{}
foreach ($ln in (SqlLines "SELECT warehouse_id, COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL GROUP BY warehouse_id")) {
  $f = $ln -split "`t"; if ($f.Count -ge 2) { $sMap[[int]$f[0].Trim()] = [decimal]$f[1].Trim() }
}
$lMap = @{}
foreach ($ln in (SqlLines "SELECT warehouse_id, COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE product_id IS NOT NULL GROUP BY warehouse_id")) {
  $f = $ln -split "`t"; if ($f.Count -ge 2) { $lMap[[int]$f[0].Trim()] = [decimal]$f[1].Trim() }
}
$pDiff = 0; $pRows = 0
foreach ($k in $sMap.Keys) {
  $pRows++
  $lv = [decimal]0
  if ($lMap.ContainsKey($k)) { $lv = $lMap[$k] }
  if ([Math]::Abs([double]($sMap[$k] - $lv)) -gt 0.0001) { $pDiff++; Info ('  product warehouse ' + $k + ': stock=' + $sMap[$k] + ' log=' + $lv) }
}
$pStockTotal = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE product_id IS NOT NULL")
$pLogTotal = D (SqlOne "SELECT COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE product_id IS NOT NULL")
Write-Host ("[DB] product warehouses=$pRows totalStock=$pStockTotal totalLog=$pLogTotal warehousesWithDiff=$pDiff")
Ok (($pDiff -eq 0)) ('finished-goods stock == log in every warehouse (diffs=' + $pDiff + ')')
Ok (([Math]::Abs([double]($pStockTotal - $pLogTotal)) -lt 0.0001)) ('finished-goods totals match (' + $pStockTotal + ' = ' + $pLogTotal + ')')

# ---------------------------------------------------------------- 2) 物料 stock vs log
Step '2) materials: warehouse_stock vs warehouse_stock_log (per warehouse)'
$msMap = @{}
foreach ($ln in (SqlLines "SELECT warehouse_id, COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL GROUP BY warehouse_id")) {
  $f = $ln -split "`t"; if ($f.Count -ge 2) { $msMap[[int]$f[0].Trim()] = [decimal]$f[1].Trim() }
}
$mlMap = @{}
foreach ($ln in (SqlLines "SELECT warehouse_id, COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE material_id IS NOT NULL GROUP BY warehouse_id")) {
  $f = $ln -split "`t"; if ($f.Count -ge 2) { $mlMap[[int]$f[0].Trim()] = [decimal]$f[1].Trim() }
}
$mDiff = 0; $mRows = 0
foreach ($k in $msMap.Keys) {
  $mRows++
  $lv = [decimal]0
  if ($mlMap.ContainsKey($k)) { $lv = $mlMap[$k] }
  if ([Math]::Abs([double]($msMap[$k] - $lv)) -gt 0.0001) { $mDiff++; Info ('  material warehouse ' + $k + ': stock=' + $msMap[$k] + ' log=' + $lv) }
}
$mStockTotal = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id IS NOT NULL")
$mLogTotal = D (SqlOne "SELECT COALESCE(SUM(change_quantity),0) FROM warehouse_stock_log WHERE material_id IS NOT NULL")
Write-Host ("[DB] material warehouses=$mRows totalStock=$mStockTotal totalLog=$mLogTotal warehousesWithDiff=$mDiff")
Ok (($mDiff -eq 0)) ('material stock == log in every warehouse (diffs=' + $mDiff + ')')
Ok (([Math]::Abs([double]($mStockTotal - $mLogTotal)) -lt 0.0001)) ('material totals match (' + $mStockTotal + ' = ' + $mLogTotal + ')')

# ---------------------------------------------------------------- 3) 应收
Step '3) receivable reconciliation'
$saleAmt = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_order WHERE status='AUDITED'")
$recvAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$recvPaid = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$recvUnpaid = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_receivable WHERE source_bill_no LIKE 'XS-%'")
$badInv = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE ABS(amount - (paid_amount + unpaid_amount)) > 0.01")
$rcptAud = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_receipt WHERE status='AUDITED'")
$retAmt = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_return WHERE status='AUDITED'")
$advRows = D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='ADVANCE'")
Write-Host ("[DB] saleAudited=$saleAmt recvAmt=$recvAmt paid=$recvPaid unpaid=$recvUnpaid rowInvariantViolations=$badInv auditedReceipts=$rcptAud saleReturns=$retAmt advanceRows=$advRows")
Ok (($recvAmt -eq $saleAmt)) ('receivable total == audited sale total (' + $recvAmt + ' = ' + $saleAmt + ')')
Ok (($badInv -eq 0)) ('every receivable row satisfies amount == paid + unpaid (violations=' + $badInv + ')')
Ok (($recvPaid -le $rcptAud)) ('paid <= audited receipts amount (' + $recvPaid + ' <= ' + $rcptAud + ')')
Info ('  sale returns audited total = ' + $retAmt + ' (negative receivables: ' + (D (SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE amount < 0")) + ' rows)')

# ---------------------------------------------------------------- 4) 应付
Step '4) payable reconciliation'
$payAmt = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_payable WHERE status <> 'CANCELLED'")
$payPaid = D (SqlOne "SELECT COALESCE(SUM(paid_amount),0) FROM finance_payable WHERE status <> 'CANCELLED'")
$payUnpaid = D (SqlOne "SELECT COALESCE(SUM(unpaid_amount),0) FROM finance_payable WHERE status <> 'CANCELLED'")
# I29 FIXED (2026-09-18): voiding a ledger row now ZEROES amount as well (the pre-void amount is kept in
# remark), so the row-level identity holds on EVERY row -> the check is enforced table-wide, CANCELLED included.
$payCancelled = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED'")
$payBadInv = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE ABS(amount - (paid_amount + unpaid_amount)) > 0.01")
$payVoidMoney = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='CANCELLED' AND (IFNULL(amount,0) <> 0 OR IFNULL(unpaid_amount,0) <> 0)")
Info ('  voided (CANCELLED) payable rows = ' + $payCancelled + ' ; still carrying money = ' + $payVoidMoney)
$negPay = D (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE amount < 0")
$payRows = D (SqlOne "SELECT COUNT(*) FROM finance_payable")
$poAmt = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM purchase_order WHERE status='AUDITED'")
Write-Host ("[DB] payableRows=$payRows amount=$payAmt paid=$payPaid unpaid=$payUnpaid rowInvariantViolations=$payBadInv voidedRows=$payCancelled voidedWithMoney=$payVoidMoney negativeRows=$negPay purchaseAudited=$poAmt")
Ok (($payRows -gt 0)) ('payable rows exist (' + $payRows + ')')
Ok (($payBadInv -eq 0)) ('EVERY payable row (incl. CANCELLED) satisfies amount == paid + unpaid (violations=' + $payBadInv + ')')
Ok (($payVoidMoney -eq 0)) ('no voided payable carries amount/unpaid (bad=' + $payVoidMoney + ')')
Ok (($payUnpaid -eq ($payAmt - $payPaid))) ('payable unpaid == amount - paid (' + $payUnpaid + ' = ' + $payAmt + ' - ' + $payPaid + ')')

# ---------------------------------------------------------------- 5) 盘点
Step '5) stock-take consistency'
$takeItems = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take_item WHERE actual_quantity IS NOT NULL')
$takeBad = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take_item WHERE actual_quantity IS NOT NULL AND ABS(diff_quantity - (actual_quantity - book_quantity)) > 0.01')
$takeAud = D (SqlOne "SELECT COUNT(*) FROM inventory_stock_take WHERE status='AUDITED'")
$takeRows = D (SqlOne 'SELECT COUNT(*) FROM inventory_stock_take')
# NOTE: there is NO material_stock_take table — 物料盘点/成品盘点 share `inventory_stock_take`
# and are separated by the warehouse (material warehouse = INVENTORY + AUXILIARY).
$auxWhIds = @()
foreach ($ln in (SqlLines "SELECT id FROM warehouse WHERE warehouse_category='OUTSOURCE' OR (warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY')")) {
  $f = $ln -split "`t"
  if ($f.Count -ge 1 -and $f[0].Trim() -ne '') { $auxWhIds += [int]$f[0].Trim() }
}
$mTakeAud = 0
if ($auxWhIds.Count -gt 0) {
  $mTakeAud = D (SqlOne ("SELECT COUNT(*) FROM inventory_stock_take WHERE status='AUDITED' AND warehouse_id IN (" + ($auxWhIds -join ',') + ")"))
}
$mTakePlanGap = 2
Ok (($mTakeAud -ge $mTakePlanGap)) ('material-warehouse stock takes audited >= ' + $mTakePlanGap + ' (got ' + $mTakeAud + ')')
Write-Host ("[DB] takes=$takeRows audited=$takeAud itemsWithActual=$takeItems diffMismatch=$takeBad materialTakesAudited=$mTakeAud")
Ok (($takeAud -ge 2)) ('audited finished-goods takes >= 2 (got ' + $takeAud + ')')
Ok (($takeBad -eq 0)) ('every take item satisfies diff == actual - book (mismatches=' + $takeBad + ')')

# ---------------------------------------------------------------- 6) 留存清单
Step '6) retained-data manifest'
$tables = @(SqlLines "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema='beichen_erp' AND TABLE_NAME NOT LIKE '%_bak_%' ORDER BY TABLE_NAME")
$report = New-Object System.Collections.Generic.List[string]
$totalRows = 0
foreach ($t in $tables) {
  $n = D (SqlOne ("SELECT COUNT(*) FROM " + $t))
  if ($n -gt 0) {
    $totalRows += $n
    $line = '{0,-46} {1,8}' -f $t, $n
    $report.Add($line)
  }
}
$report | ForEach-Object { Write-Output $_ }
Write-Host ('[DB] tables with rows = ' + $report.Count + ' ; total rows = ' + $totalRows)
$report | Set-Content -Encoding UTF8 (Join-Path $PSScriptRoot 'e2e-final-20260918.txt')
Write-Host ('written -> e2e-final-20260918.txt')

# ---------------------------------------------------------------- 7) 只增不删
Step '7) only grows, never shrinks (baseline vs final)'
$basePath = Join-Path $PSScriptRoot 'e2e-baseline-20260918.txt'
$shrunk = 0; $grew = 0; $same = 0; $checked = 0
function ReadCounts([string]$path) {
  $m = @{}
  foreach ($ln in (Get-Content -Encoding UTF8 $path)) {
    if ($ln -match '^\s*([A-Za-z0-9_]+)\s+(\d+)\s*$') { $m[$Matches[1]] = [int]$Matches[2] }
  }
  return $m
}
if (Test-Path $basePath) {
  $b = ReadCounts $basePath
  $f = ReadCounts (Join-Path $PSScriptRoot 'e2e-final-20260918.txt')
  foreach ($k in $b.Keys) {
    if (-not $f.ContainsKey($k)) { $f[$k] = 0 }
    $checked++
    if ($f[$k] -lt $b[$k]) { $shrunk++; Info ('  SHRUNK ' + $k + ': ' + $b[$k] + ' -> ' + $f[$k]) }
    elseif ($f[$k] -gt $b[$k]) { $grew++ }
    else { $same++ }
  }
  Write-Host ('[DB] baseline tables checked=' + $checked + ' grew=' + $grew + ' unchanged=' + $same + ' SHRUNK=' + $shrunk)
  Ok (($shrunk -eq 0)) ('no baseline table lost rows (shrunk=' + $shrunk + ')')
  Ok (($grew -gt 0)) ('data grew as expected (grew=' + $grew + ')')
} else {
  Ok $false ('baseline file missing: ' + $basePath)
}

Write-Host ('RESULT recon PASS=' + $pass + ' FAIL=' + $fail)
