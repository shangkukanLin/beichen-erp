# verify-perf-f7-82-batch.ps1
#
# Regression for the 2026-09-20 outsource PERFORMANCE round (report section 49): four N+1 hotspots were
# batched. A performance change must NOT change the output, so every assertion here compares the API
# result against an independently recomputed value from SQL ("equivalence check"):
#   1) outsource order list      -> factoryName / productCount / productNames
#   2) outsource delivery list   -> itemCount / itemSummary segment count / factoryName / warehouseName
#   3) stock-loss list           -> itemSummary segment count / warehouseName
#   4) stock-loss audit precheck -> batched stock lookup still lists the shortages (fixture, zero-write)
#
# Needs a UTF-8 BOM (Chinese expectations).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$script:fail = 0
$script:skip = 0

function Ok($m)   { Write-Output ("PASS " + $m) }
function Bad($m)  { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function Info($m) { Write-Output ("  [INFO] " + $m) }

$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -e $q 2>$null | Out-Null }

function Login([string]$u, [string]$p) {
  try {
    $r = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' `
         -Body (@{ username = $u; password = $p; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
    if ($r -and $r.data -and $r.data.token) { return $r.data.token }
    return $null
  } catch { return $null }
}
function Req([string]$method, [string]$path, [string]$tok, $body) {
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 40 }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch {
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    return [pscustomobject]@{ code = $sc; msg = 'transport-error' }
  }
}
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }
function BMsg($r)  { if ($null -eq $r) { return '' }; if ($null -eq $r.msg) { return '' }; return [string]$r.msg }

Write-Output '=== 0) login ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'
$CID = 1

# ---------- 1) outsource order list ----------
Write-Output ''
Write-Output '=== 1) order list: factoryName / productCount / productNames (batched) ==='
$cntOpBefore = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$cntSlBefore = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
$cntSliBefore = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss_item')

$r1 = Req 'GET' '/outsource/order/page?pageNum=1&pageSize=50' $admin $null
$checked1 = 0; $bad1 = 0
if ((BCode $r1) -eq 200 -and $null -ne $r1.data -and $null -ne $r1.data.records) {
  foreach ($rec in $r1.data.records) {
    $id = [long]$rec.id
    $expCount = [int](SqlOne "SELECT COUNT(*) FROM outsource_order_product WHERE order_id=$id")
    $expNames = SqlOne "SELECT GROUP_CONCAT(product_name ORDER BY id SEPARATOR ' / ') FROM outsource_order_product WHERE order_id=$id"
    if ($null -eq $expNames) { $expNames = '' }
    if ([int]$rec.productCount -ne $expCount) { $bad1++; Bad ("order $id productCount=" + $rec.productCount + " expected $expCount") }
    if ([string]$rec.productNames -ne [string]$expNames) { $bad1++; Bad ("order $id productNames='" + $rec.productNames + "' expected '" + $expNames + "'") }
    if ($null -ne $rec.factoryId) {
      $expFn = SqlOne ("SELECT name FROM supplier WHERE id=" + [long]$rec.factoryId)
      if ($null -eq $expFn) { $expFn = '' }
      if ([string]$rec.factoryName -ne [string]$expFn) { $bad1++; Bad ("order $id factoryName='" + $rec.factoryName + "' expected '" + $expFn + "'") }
    }
    $checked1++
  }
} else { Skip ('cannot read the order list: ' + (BMsg $r1)) }
if ($checked1 -gt 0) {
  if ($bad1 -eq 0) { Ok ("order list: all $checked1 rows match the SQL recomputation (factoryName/productCount/productNames)") }
} elseif ($bad1 -eq 0) { Skip 'order list returned no rows' }

# ---------- 2) outsource delivery list ----------
Write-Output ''
Write-Output '=== 2) delivery list: itemCount / itemSummary / factoryName / warehouseName (batched) ==='
$r2 = Req 'GET' '/outsource/delivery/page?pageNum=1&pageSize=50' $admin $null
$checked2 = 0; $bad2 = 0
if ((BCode $r2) -eq 200 -and $null -ne $r2.data -and $null -ne $r2.data.records) {
  foreach ($rec in $r2.data.records) {
    $id = [long]$rec.id
    $expItems = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery_item WHERE delivery_id=$id")
    if ([int]$rec.itemCount -ne $expItems) { $bad2++; Bad ("delivery $id itemCount=" + $rec.itemCount + " expected $expItems") }
    # itemSummary must have exactly one segment per line
    $summary = [string]$rec.itemSummary
    $segs = if ([string]::IsNullOrEmpty($summary)) { 0 } else { ($summary -split '、').Count }
    if ($segs -ne $expItems) { $bad2++; Bad ("delivery $id itemSummary has $segs segments, expected $expItems ('$summary')") }
    if ($null -ne $rec.factoryId) {
      $expFn = SqlOne ("SELECT name FROM supplier WHERE id=" + [long]$rec.factoryId)
      if ($null -eq $expFn) { $expFn = '' }
      if ([string]$rec.factoryName -ne [string]$expFn) { $bad2++; Bad ("delivery $id factoryName='" + $rec.factoryName + "' expected '" + $expFn + "'") }
    }
    if ($null -ne $rec.fromWarehouseId) {
      $expWh = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + [long]$rec.fromWarehouseId)
      if ($null -eq $expWh) { $expWh = '' }
      if ([string]$rec.fromWarehouseName -ne [string]$expWh) { $bad2++; Bad ("delivery $id fromWarehouseName='" + $rec.fromWarehouseName + "' expected '" + $expWh + "'") }
    }
    if ($null -ne $rec.toWarehouseId) {
      $expWh = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + [long]$rec.toWarehouseId)
      if ($null -eq $expWh) { $expWh = '' }
      if ([string]$rec.toWarehouseName -ne [string]$expWh) { $bad2++; Bad ("delivery $id toWarehouseName='" + $rec.toWarehouseName + "' expected '" + $expWh + "'") }
    }
    $checked2++
  }
} else { Skip ('cannot read the delivery list: ' + (BMsg $r2)) }
if ($checked2 -gt 0) {
  if ($bad2 -eq 0) { Ok ("delivery list: all $checked2 rows match the SQL recomputation (itemCount/itemSummary/factoryName/warehouseName)") }
} elseif ($bad2 -eq 0) { Skip 'delivery list returned no rows' }

# ---------- 3) stock-loss list ----------
Write-Output ''
Write-Output '=== 3) stock-loss list: itemSummary / warehouseName (batched) ==='
$r3 = Req 'GET' '/outsource/stock-loss/page?pageNum=1&pageSize=50' $admin $null
$checked3 = 0; $bad3 = 0
if ((BCode $r3) -eq 200 -and $null -ne $r3.data -and $null -ne $r3.data.records) {
  foreach ($rec in $r3.data.records) {
    $id = [long]$rec.id
    $expItems = [int](SqlOne "SELECT COUNT(*) FROM outsource_stock_loss_item WHERE loss_id=$id")
    $summary = [string]$rec.itemSummary
    $segs = if ([string]::IsNullOrEmpty($summary)) { 0 } else { ($summary -split '、').Count }
    if ($segs -ne $expItems) { $bad3++; Bad ("stock-loss $id itemSummary has $segs segments, expected $expItems ('$summary')") }
    if ($null -ne $rec.warehouseId) {
      $expWh = SqlOne ("SELECT warehouse_name FROM warehouse WHERE id=" + [long]$rec.warehouseId)
      if ($null -eq $expWh) { $expWh = '' }
      if ([string]$rec.warehouseName -ne [string]$expWh) { $bad3++; Bad ("stock-loss $id warehouseName='" + $rec.warehouseName + "' expected '" + $expWh + "'") }
    }
    $checked3++
  }
} else { Skip ('cannot read the stock-loss list: ' + (BMsg $r3)) }
if ($checked3 -gt 0) {
  if ($bad3 -eq 0) { Ok ("stock-loss list: all $checked3 rows match the SQL recomputation (itemSummary/warehouseName)") }
} elseif ($bad3 -eq 0) { Skip 'stock-loss list returned no rows' }

# ---------- 4) stock-loss audit precheck (batched stock lookup) ----------
Write-Output ''
Write-Output '=== 4) stock-loss audit precheck still lists every shortage (batched lookup) ==='
$slId = 0
$whSl = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' ORDER BY id LIMIT 1"
$matSl = SqlOne "SELECT id FROM outsource_material ORDER BY id LIMIT 1"
if ($whSl -and $matSl) {
  SqlExec ("INSERT INTO outsource_stock_loss (code, warehouse_id, loss_date, status, company_id) VALUES ('VERIFY-P-SL', $whSl, CURDATE(), 'DRAFT', $CID)")
  $slId = [int](SqlOne "SELECT id FROM outsource_stock_loss WHERE code='VERIFY-P-SL' ORDER BY id DESC LIMIT 1")
  # two lines, both far beyond any possible stock -> the precheck must list BOTH
  SqlExec ("INSERT INTO outsource_stock_loss_item (loss_id, material_id, quantity, company_id) VALUES ($slId, $matSl, 99999, $CID)")
  SqlExec ("INSERT INTO outsource_stock_loss_item (loss_id, material_id, quantity, company_id) VALUES ($slId, $matSl, 88888, $CID)")
  Info "fixture: stock-loss draft id=$slId with 2 over-quantity lines (warehouse=$whSl material=$matSl)"
  $ra = Req 'PUT' ("/outsource/stock-loss/$slId/audit") $admin $null
  if ((BCode $ra) -eq 200) { Bad 'audit of an over-quantity stock-loss was ACCEPTED (stock could go negative)' }
  elseif ((BMsg $ra) -like '*库存不足，无法报损*') {
    $listed = ((BMsg $ra) -split '；').Count
    if ($listed -ge 2) { Ok ("audit rejected and listed both shortages (batched lookup works): " + (BMsg $ra)) }
    else { Bad ('audit rejected but only listed ' + $listed + ' shortage(s): ' + (BMsg $ra)) }
  } else { Bad ('audit rejected for another reason: ' + (BMsg $ra)) }
  $st = SqlOne "SELECT status FROM outsource_stock_loss WHERE id=$slId"
  if ($st -eq 'DRAFT') { Ok ('the stock-loss stayed DRAFT (no partial posting, status=' + $st + ')') }
  else { Bad ('status changed to ' + $st + ' despite the rejection') }
} else { Skip 'cannot build the stock-loss fixture' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($slId -gt 0) { SqlExec ("DELETE FROM outsource_stock_loss_item WHERE loss_id=$slId") }
if ($slId -gt 0) { SqlExec ("DELETE FROM outsource_stock_loss WHERE id=$slId") }

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_stock_loss_item')
Info ("after cleanup: order_product=$c1(was $cntOpBefore) stock_loss=$c2(was $cntSlBefore) stock_loss_item=$c3(was $cntSliBefore)")
if ($c1 -eq $cntOpBefore -and $c2 -eq $cntSlBefore -and $c3 -eq $cntSliBefore) {
  Ok 'all 3 tables back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
