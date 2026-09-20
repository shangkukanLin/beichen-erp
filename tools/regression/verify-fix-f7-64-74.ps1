# verify-fix-f7-64-74.ps1
#
# Regression for the C-batch behavioural fixes of the 2026-09-20 outsource fix (report section 48.1):
#   F7-64  defect-return un-audit must be the EXACT inverse of audit (was: clamped to current stock,
#          so material could only be partially taken back => the outsource warehouse permanently gains stock)
#   F7-74  "factory warehouse" must be resolved as an OUTSOURCE-category warehouse and must fail loudly
#          when there is none (was: first warehouse of the factory, taken by factory_id only)
#
# FIXTURES: this script DOES perform real writes (audit / un-audit of a defect-return draft) because
# that is the only way to observe the asymmetry. Everything is restored afterwards and 8 tables are
# count/value checked at the end. Needs a UTF-8 BOM (Chinese expectations).

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
# first numeric value of the first row (0 when the query returns nothing or NULL) -- used for stock reads
function SqlFirstNum([string]$q) {
  $v = SqlOne $q
  if ([string]::IsNullOrWhiteSpace($v)) { return 0 }
  try { return [double]$v } catch { return 0 }
}

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

Write-Output '=== 0) login + baseline ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'
$CID = 1

$cntSup = [int](SqlOne 'SELECT COUNT(*) FROM supplier')
$cntWh  = [int](SqlOne 'SELECT COUNT(*) FROM warehouse')
$cntOrd = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$cntOp  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$cntDv  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$cntStk = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
$cntPay = [int](SqlOne 'SELECT COUNT(*) FROM finance_payable')
$maxDv  = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM outsource_order_delivery')
# NOTE: payable rows are cleaned by "id > the id captured here" -- finance_payable uses bill_no for the
# SYSTEM bill number and source_bill_no for the source document, so matching by code would be wrong.
$maxPayId = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_payable')
Info "baseline: supplier=$cntSup warehouse=$cntWh order=$cntOrd orderProduct=$cntOp delivery=$cntDv stock=$cntStk payable=$cntPay maxDeliveryId=$maxDv maxPayableId=$maxPayId"

# ---------- F7-74 : a factory whose only warehouse is NOT an outsource warehouse ----------
Write-Output ''
Write-Output '=== F7-74) factory warehouse must be an OUTSOURCE warehouse (else fail loudly) ==='
$fixtureSup = 0; $fixtureWh = 0; $fixtureOrd = 0; $fixtureOp = 0
$master62 = SqlOne "SELECT product_id FROM outsource_order_product WHERE product_id IS NOT NULL ORDER BY id LIMIT 1"
# NOTE: pick the warehouse that ACTUALLY holds A-grade stock of this product -- a defect-return audit
# debits the finished-goods stock, so an empty warehouse makes the fixture fail with "库存不足".
$finWh73  = SqlOne "SELECT warehouse_id FROM warehouse_stock WHERE product_id=$master62 AND quality_type='A' AND quantity>0 ORDER BY warehouse_id LIMIT 1"
$finQty73 = SqlFirstNum "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$finWh73 AND product_id=$master62 AND quality_type='A'"
Info "finished-goods source: warehouse=$finWh73 product=$master62 A-grade qty=$finQty73"
if ($master62 -and $finWh73) {
  SqlExec ("INSERT INTO supplier (code, name, status, company_id) VALUES ('VERIFY-F-SUP', 'VERIFY-F-SUP', 1, $CID)")
  $fixtureSup = [int](SqlOne "SELECT id FROM supplier WHERE code='VERIFY-F-SUP' ORDER BY id DESC LIMIT 1")
  # deliberately create only an INVENTORY warehouse for that factory (no OUTSOURCE one)
  SqlExec ("INSERT INTO warehouse (code, warehouse_name, warehouse_category, warehouse_type, factory_id, status, company_id) VALUES ('VERIFY-F-WH', 'VERIFY-F-WH', 'INVENTORY', 'FINISHED', $fixtureSup, 1, $CID)")
  $fixtureWh = [int](SqlOne "SELECT id FROM warehouse WHERE code='VERIFY-F-WH' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_order (code, factory_id, status, company_id) VALUES ('VERIFY-F-ORD', $fixtureSup, 'PRODUCING', $CID)")
  $fixtureOrd = [int](SqlOne "SELECT id FROM outsource_order WHERE code='VERIFY-F-ORD' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_order_product (order_id, product_name, product_id, quantity, company_id) VALUES ($fixtureOrd, 'VERIFY-F-P', $master62, 100, $CID)")
  $fixtureOp = [int](SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$fixtureOrd ORDER BY id LIMIT 1")
  Info "fixture: supplier=$fixtureSup nonOutsourceWh=$fixtureWh order=$fixtureOrd product=$fixtureOp"
  $body74 = @{ orderId = [long]$fixtureOrd; productId = [long]$fixtureOp; productMasterId = [long]$master62
               quantity = 1; aQty = 1; bQty = 0; cQty = 0; defectQty = 0; warehouseId = [long]$finWh73 }
  $r74 = Req 'POST' '/outsource/order-delivery' $admin $body74
  if ((BCode $r74) -eq 200) { Bad 'delivery ACCEPTED for a factory without an outsource warehouse (F7-74 not fixed)' }
  elseif ((BMsg $r74) -like '*未配置委外仓库*') { Ok ('rejected: ' + (BMsg $r74) + '  => category filter + loud failure work (F7-74)') }
  else { Bad ('rejected for another reason: ' + (BMsg $r74)) }
} else { Skip 'cannot build the F7-74 fixture' }

# ---------- F7-64 : un-audit must be the exact inverse of audit ----------
Write-Output ''
Write-Output '=== F7-64) defect-return un-audit is the exact inverse (no clamping) ==='
$dvId = 0; $whOut = 0; $matId = 0; $beforeOut = 0; $beforeStockRow = 0
$ord58 = SqlOne "SELECT id FROM outsource_order WHERE status='PRODUCING' ORDER BY id LIMIT 1"
$row58 = SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$ord58 ORDER BY id LIMIT 1"
$master58 = SqlOne "SELECT product_id FROM outsource_order_product WHERE id=$row58"
$factory58 = SqlOne "SELECT factory_id FROM outsource_order WHERE id=$ord58"
if ($ord58 -and $row58 -and $factory58) {
  $whOut = [int](SqlOne "SELECT id FROM warehouse WHERE factory_id=$factory58 AND warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1")
  $matId = [int](SqlOne "SELECT outsource_material_id FROM outsource_order_material WHERE product_id=$row58 AND outsource_material_id IS NOT NULL ORDER BY outsource_material_id LIMIT 1")
  Info "fixture source: order=$ord58 row=$row58 master=$master58 factory=$factory58 outsourceWh=$whOut material=$matId"
  if ($whOut -gt 0 -and $matId -gt 0) {
    $beforeOut = [double](SqlFirstNum "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
    $beforeStockRow = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
    Info ("before: outsource-warehouse material $matId quantity = " + $beforeOut + " (row exists=" + ($beforeStockRow -gt 0) + ")")
    # a DRAFT defect-return delivery (-10) written on the CURRENT product row
    SqlExec ("INSERT INTO outsource_order_delivery (order_id, product_id, product_master_id, quantity, a_qty, b_qty, c_qty, defect_qty, quality_type, warehouse_id, status, delivery_type, is_reverse, company_id) VALUES ($ord58, $row58, $master58, -10, 0, 0, 0, 10, 'A', $finWh73, 'DRAFT', 'DEFECT_RETURN', 1, $CID)")
    $dvId = [int](SqlOne "SELECT id FROM outsource_order_delivery WHERE order_id=$ord58 ORDER BY id DESC LIMIT 1")
    $ra = Req 'PUT' ("/outsource/order-delivery/$dvId/audit") $admin $null
    $afterAudit = [double](SqlFirstNum "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
    Info ("after audit: outsource-warehouse quantity = " + $afterAudit + "  (expected " + ($beforeOut + 10) + ")")
    if ((BCode $ra) -ne 200) { Bad ('defect-return audit failed: ' + (BMsg $ra)) }
    elseif ([math]::Abs($afterAudit - ($beforeOut + 10)) -lt 0.001) { Ok ('audit returned +10 material to the outsource warehouse') }
    else { Bad ("audit produced $afterAudit (expected " + ($beforeOut + 10) + ")") }

    # simulate "the returned material has already been consumed" -> force the warehouse row to 0
    SqlExec ("UPDATE warehouse_stock SET quantity=0 WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
    $ru = Req 'PUT' ("/outsource/order-delivery/$dvId/un-audit") $admin $null
    $afterUn = [double](SqlFirstNum "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
    Info ("after un-audit: outsource-warehouse quantity = " + $afterUn + "  (expected -10; the old clamped logic would give 0)")
    if ((BCode $ru) -ne 200) { Bad ('defect-return un-audit failed: ' + (BMsg $ru)) }
    elseif ([math]::Abs($afterUn - (-10)) -lt 0.001) { Ok 'un-audit took back the full amount (stock -10, exactly inverse of audit) - F7-64 fixed' }
    else { Bad ("un-audit produced $afterUn (expected -10 => clamping still present)") }
  } else { Skip 'no outsource warehouse / BOM material for the F7-64 fixture' }
} else { Skip 'no PRODUCING order for the F7-64 fixture' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($dvId -gt 0) { SqlExec ("DELETE FROM finance_payable WHERE id > $maxPayId") }
if ($dvId -gt 0) { SqlExec ("DELETE FROM outsource_order_delivery WHERE id=$dvId") }
if ($whOut -gt 0 -and $matId -gt 0) {
  if ($beforeStockRow -gt 0) {
    SqlExec ("UPDATE warehouse_stock SET quantity=$beforeOut WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
  } else {
    SqlExec ("DELETE FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
  }
}
if ($fixtureOp -gt 0)    { SqlExec ("DELETE FROM outsource_order_product WHERE id=$fixtureOp") }
if ($fixtureOrd -gt 0)   { SqlExec ("DELETE FROM outsource_order WHERE id=$fixtureOrd") }
if ($fixtureWh -gt 0)    { SqlExec ("DELETE FROM warehouse WHERE id=$fixtureWh") }
if ($fixtureSup -gt 0)   { SqlExec ("DELETE FROM supplier WHERE id=$fixtureSup") }

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM supplier')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM warehouse')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$c5 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$c6 = [int](SqlOne 'SELECT COUNT(*) FROM warehouse_stock')
$c7 = [int](SqlOne 'SELECT COUNT(*) FROM finance_payable')
Info ("after cleanup: supplier=$c1(was $cntSup) warehouse=$c2(was $cntWh) order=$c3(was $cntOrd) orderProduct=$c4(was $cntOp) delivery=$c5(was $cntDv) stock=$c6(was $cntStk) payable=$c7(was $cntPay)")
if ($c1 -eq $cntSup -and $c2 -eq $cntWh -and $c3 -eq $cntOrd -and $c4 -eq $cntOp -and $c5 -eq $cntDv -and $c6 -eq $cntStk -and $c7 -eq $cntPay) {
  Ok 'all 7 tables back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }
if ($whOut -gt 0 -and $matId -gt 0) {
  $finalQty = [double](SqlFirstNum "SELECT quantity FROM warehouse_stock WHERE warehouse_id=$whOut AND material_id=$matId AND quality_type='GOOD'")
  if ([math]::Abs($finalQty - $beforeOut) -lt 0.001) { Ok "the outsource-warehouse quantity was restored to $beforeOut" }
  else { Bad "the outsource-warehouse quantity is $finalQty (expected $beforeOut)" }
}

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
