# Reconcile the material-side "on-site for repair" ledger against the documents.
#
# WHY: until 2026-09-27 `cancelRepairReturn()` restored the on-site row only when the return had
#   material-usage lines (`if (!mats.isEmpty())`), while `repairReturn()` deduced it UNCONDITIONALLY
#   => every cancellation of a return WITHOUT material lines leaked the on-site quantity forever
#   (the row even went negative). The code is fixed; this script repairs the DATA left behind.
#
# WHAT IT CHECKS (per company + supplier-outsource-warehouse + material):
#   expected = SUM(sent)  - SUM(still-existing returns)     over AUDITED repair documents
#   actual   = warehouse_stock row with stock_form = 'MATERIAL_REPAIR'
#   diff > 0 => the ledger MISSES units (the leak);  diff < 0 => the ledger has units no document backs.
#
# FORM-ERA TEST (why not just "all AUDITED docs"): documents audited BEFORE the 2026-09-25 "material
#   form" change never entered the on-site row at all (and their returns skip the deduction too, so
#   they are symmetric and must NOT be counted). A document counts as form-era only when the audit
#   leg is actually present in the stock log: SUM(MATERIAL_REPAIR_STOCK_IN legs for doc+material) >= sent.
#   Anything smaller is inconclusive (legacy, or only restores) => excluded, and printed as EXCLUDED.
#
# READ-ONLY by default. -Apply writes the correction AND a stock-log row per write (an unattended
#   ledger edit would be invisible later; see "unlogged manual change" detection below).
# PURE ASCII.
param([switch]$Apply)

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$today = (Get-Date).ToString('yyyyMMdd')

function SqlLines([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = @($o | ForEach-Object { "$_" } | Where-Object { "$_".Trim() -ne '' })
  if ($ls.Count -le 1) { return @() }
  return @($ls[1..($ls.Count - 1)])
}
function SqlScalar([string]$q) { $r = @(SqlLines $q); if ($r.Count -eq 0) { return '' }; return (("$($r[0])" -split "`t")[0]).Trim() }
function D([string]$s) { if (-not $s -or "$s".Trim() -eq '') { return [decimal]0 }; return [decimal]$s }
function F([decimal]$v) { return $v.ToString([Globalization.CultureInfo]::InvariantCulture) }
# Chinese remarks without putting non-ASCII bytes in this file (PS 5.1 parses BOM-less CJK badly)
function CN([int[]]$cp) { return (-join ($cp | ForEach-Object { [char]$_ })) }
$R_ON_SITE = CN @(0x5BF9, 0x8D26, 0x5DEE, 0x5F02, 0x4FEE, 0x6B63)          # 对账差异修正
$R_UNLOGGED = CN @(0x5386, 0x53F2, 0x6539, 0x6570, 0x672A, 0x7559, 0x75D5, 0x8865, 0x8BB0)  # 历史改数未留痕补记

Write-Host '=== input: AUDITED repair docs (per doc + material) ==='
$docs = SqlLines "SELECT h.id, h.company_id, h.supplier_id, i.outsource_material_id, SUM(i.quantity) FROM outsource_material_return h JOIN outsource_material_return_item i ON i.return_order_id = h.id WHERE h.return_type = 'REPAIR' AND h.status = 'AUDITED' GROUP BY h.id, h.company_id, h.supplier_id, i.outsource_material_id"
$legs = @{}
foreach ($l in @(SqlLines "SELECT related_bill_id, material_id, SUM(change_quantity) FROM warehouse_stock_log WHERE stock_form = 'MATERIAL_REPAIR' AND change_type = 'MATERIAL_REPAIR_STOCK_IN' GROUP BY related_bill_id, material_id")) {
  $f = "$l" -split "`t"; $legs["$($f[0])|$($f[1])"] = D $f[2]
}
$retOf = @{}; $retStatus = @{}
foreach ($l in @(SqlLines "SELECT r.return_order_id, r.material_id, SUM(r.quantity) FROM outsource_material_return_repair r GROUP BY r.return_order_id, r.material_id")) {
  $f = "$l" -split "`t"; $retOf["$($f[0])|$($f[1])"] = D $f[2]
}
$supWh = @{}
foreach ($l in @(SqlLines "SELECT factory_id, MIN(id) FROM warehouse WHERE warehouse_category = 'OUTSOURCE' GROUP BY factory_id")) {
  $f = "$l" -split "`t"; $supWh["$($f[0])"] = [int]$f[1]
}
$matName = @{}
foreach ($l in @(SqlLines 'SELECT id, material_name FROM outsource_material')) { $f = "$l" -split "`t"; $matName["$($f[0])"] = "$($f[1])" }
$whName = @{}
foreach ($l in @(SqlLines 'SELECT id, warehouse_name FROM warehouse')) { $f = "$l" -split "`t"; $whName["$($f[0])"] = "$($f[1])" }

$expected = @{}; $excluded = 0
foreach ($l in $docs) {
  $f = "$l" -split "`t"
  $docId = "$($f[0])"; $cid = "$($f[1])"; $sup = "$($f[2])"; $mat = "$($f[3])"; $sent = D $f[4]
  if (-not $supWh.ContainsKey($sup)) { Write-Host ("  doc " + $docId + " EXCLUDED (supplier " + $sup + " has no outsource warehouse)"); $excluded++; continue }
  $wh = "$($supWh[$sup])"
  $legSum = if ($legs.ContainsKey("$docId|$mat")) { $legs["$docId|$mat"] } else { [decimal]0 }
  $live = if ($retOf.ContainsKey("$docId|$mat")) { $retOf["$docId|$mat"] } else { [decimal]0 }
  $era = ($legSum -ge $sent)
  if ($era) {
    $k = "$cid|$wh|$mat"
    if (-not $expected.ContainsKey($k)) { $expected[$k] = [decimal]0 }
    $expected[$k] = $expected[$k] + ($sent - $live)
    Write-Host ("  doc " + $docId + " FORM-ERA  wh=" + $wh + " mat=" + $mat + " sent=" + (F $sent) + " returns=" + (F $live) + " (audit legs " + (F $legSum) + ")")
  } else {
    Write-Host ("  doc " + $docId + " EXCLUDED (legacy: audit legs " + (F $legSum) + " < sent " + (F $sent) + ")")
    $excluded++
  }
}
Write-Host ("docs audited forms counted above; excluded=" + $excluded)

Write-Host ''
Write-Host '=== actual on-site rows (warehouse_stock form=MATERIAL_REPAIR) ==='
$actualMap = @{}
foreach ($l in @(SqlLines "SELECT ws.id, ws.company_id, ws.warehouse_id, ws.material_id, ws.quantity FROM warehouse_stock ws WHERE ws.stock_form = 'MATERIAL_REPAIR'")) {
  $f = "$l" -split "`t"
  $actualMap["$($f[1])|$($f[2])|$($f[3])"] = @{ Id = [int]$f[0]; Qty = (D $f[4]) }
  Write-Host ("  row " + $f[0] + " cid=" + $f[1] + " wh=" + $f[2] + "(" + $whName["$($f[2])"] + ") mat=" + $f[3] + "(" + $matName["$($f[3])"] + ") qty=" + (F (D $f[4])))
}
# stock log sum per key: a row whose quantity differs from SUM(change_quantity) was edited without a log row
$logSum = @{}
foreach ($l in @(SqlLines "SELECT warehouse_id, material_id, SUM(change_quantity) FROM warehouse_stock_log WHERE stock_form = 'MATERIAL_REPAIR' GROUP BY warehouse_id, material_id")) {
  $f = "$l" -split "`t"; $logSum["$($f[0])|$($f[1])"] = D $f[2]
}

Write-Host ''
Write-Host '=== comparison ==='
$keys = @($expected.Keys) + @($actualMap.Keys) | Sort-Object -Unique
$diffs = 0; $mismatch = 0; $sql = @()
foreach ($k in $keys) {
  $p = "$k" -split '\|'
  $exp = if ($expected.ContainsKey($k)) { $expected[$k] } else { [decimal]0 }
  $has = $actualMap.ContainsKey($k)
  $act = if ($has) { $actualMap[$k].Qty } else { [decimal]0 }
  $dif = $exp - $act
  $ls = if ($logSum.ContainsKey("$($p[1])|$($p[2])")) { $logSum["$($p[1])|$($p[2])"] } else { [decimal]0 }
  $tag = if ($dif -gt 0) { 'MISSING (leak)' } elseif ($dif -lt 0) { 'EXTRA (no document)' } else { 'ok' }
  if ($dif -ne 0) { $diffs++ }
  Write-Host ("  cid=" + $p[0] + " wh=" + $p[1] + "(" + $whName[$p[1]] + ") mat=" + $p[2] + "(" + $matName[$p[2]] + ") expected=" + (F $exp) + " actual=" + (F $act) + " diff=" + (F $dif) + "  " + $tag)
  if ($has -and $ls -ne $act) {
    # the row was changed by hand at some point without writing a stock-log row -> restore the audit trail first
    $mismatch++
    Write-Host ("      ! ledger edited without a log row: log sum=" + (F $ls) + " vs quantity=" + (F $act) + " (delta " + (F ($act - $ls)) + ")")
    if ($Apply) {
      $mk = if ($act - $ls -gt 0) { 'OTHER_IN' } else { 'OTHER_OUT' }
      $b = $act - $ls
      $sql += ("INSERT INTO warehouse_stock_log (warehouse_id, material_id, stock_form, quality_type, change_type, change_quantity, before_quantity, after_quantity, related_bill_no, remark, company_id) VALUES (" + $p[1] + ", " + $p[2] + ", 'MATERIAL_REPAIR', 'GOOD', '" + $mk + "', " + (F $b) + ", " + (F ($ls)) + ", " + (F $act) + ", 'RECON-" + $today + "', '" + $R_UNLOGGED + "', " + $p[0] + ");")
    }
  }
  if ($Apply -and $dif -ne 0) {
    $sql += ("UPDATE warehouse_stock SET quantity = " + (F $exp) + " WHERE id = " + $actualMap[$k].Id + ";")
    if (-not $has) { Write-Host '      ! no row to update (expected a row to exist) - skipped' }
    $sql += ("INSERT INTO warehouse_stock_log (warehouse_id, material_id, stock_form, quality_type, change_type, change_quantity, before_quantity, after_quantity, related_bill_no, remark, company_id) VALUES (" + $p[1] + ", " + $p[2] + ", 'MATERIAL_REPAIR', 'GOOD', '" + $(if ($dif -gt 0) { 'MATERIAL_REPAIR_STOCK_IN' } else { 'OTHER_OUT' }) + "', " + (F $dif) + ", " + (F $act) + ", " + (F $exp) + ", 'RECON-" + $today + "', '" + $R_ON_SITE + "', " + $p[0] + ");")
  }
}

if ($Apply -and $sql.Count -gt 0) {
  $f = Join-Path $env:TEMP 'reconcile-repair-onsite.sql'
  [IO.File]::WriteAllText($f, ($sql -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
  Write-Host ''
  Write-Host ('=== APPLY (' + $sql.Count + ' statements) ===')
  & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e ("source " + ($f -replace '\\', '/')) 2>$null | Out-Null
  $after = SqlScalar "SELECT GROUP_CONCAT(CONCAT(warehouse_id,':',material_id,':',quantity) ORDER BY id) FROM warehouse_stock WHERE stock_form = 'MATERIAL_REPAIR'"
  Write-Host ('  rows after apply: ' + $after)
}

Write-Host ''
Write-Host ('SUMMARY diffs=' + $diffs + ' unlogged-edits=' + $mismatch + ' apply=' + [bool]$Apply)
if (-not $Apply -and $diffs -gt 0) {
  Write-Host 'RESULT DIFF reconcile-repair-onsite (re-run with -Apply to fix; every write is logged)'
  exit 1
}
Write-Host 'RESULT PASS reconcile-repair-onsite'
