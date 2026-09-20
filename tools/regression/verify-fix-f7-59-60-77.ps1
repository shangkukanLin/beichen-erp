# verify-fix-f7-59-60-77.ps1
#
# Regression for the B-batch "one implementation" step of the 2026-09-20 outsource fix (report 47.1):
#   F7-59  "delivered qty" must be matched by PRODUCT MASTER id first (row id only as fallback) --
#          so that rebuilding the order-product rows (order edit) does NOT lose history.
#   F7-60  "material requirement" must use ONE perUnit rule (quantity_per_set first, else re-derive);
#          verified as a consistency sample between the list page and the detail page.
#   F7-77  material unit price must be ONE implementation: CANCELLED orders excluded everywhere
#          (weighted price + both FIFO endpoints).
#
# FIXTURES: created via SQL/API, removed at the end, counts self-checked. Needs a UTF-8 BOM.

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
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -D $DB -e $q 2>$null | Out-Null }

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

$cntOrd = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$cntOp  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$cntOd  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$cntMat = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material')
$cntMo  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$cntMoi = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
Info "baseline: order=$cntOrd orderProduct=$cntOp orderDelivery=$cntOd material=$cntMat mo=$cntMo moi=$cntMoi"

$factoryId = SqlOne "SELECT factory_id FROM outsource_order WHERE factory_id IS NOT NULL ORDER BY id LIMIT 1"
$master    = SqlOne "SELECT product_id FROM outsource_order_product WHERE product_id IS NOT NULL ORDER BY id LIMIT 1"
$finWh     = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' AND status=1 ORDER BY id LIMIT 1"
$supId     = SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1'

# ---------- F7-59 ----------
Write-Output ''
Write-Output '=== F7-59) delivered qty matched by product MASTER id (survives product-row rebuild) ==='
$ordId = 0; $rowA = 0; $rowB = 0; $dvHist = 0; $dvEdit = 0
if ($factoryId -and $master -and $finWh) {
  SqlExec ("INSERT INTO outsource_order (code, factory_id, status, company_id) VALUES ('VERIFY-D-ORD', $factoryId, 'PENDING', $CID)")
  $ordId = [int](SqlOne "SELECT id FROM outsource_order WHERE code='VERIFY-D-ORD' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_order_product (order_id, product_name, product_id, quantity, company_id) VALUES ($ordId, 'VERIFY-D-P', $master, 100, $CID)")
  $rowA = [int](SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$ordId ORDER BY id LIMIT 1")
  # (1) HISTORICAL draft of 30, attached to the OLD row id (exactly what an order-edit leaves behind)
  SqlExec ("INSERT INTO outsource_order_delivery (order_id, product_id, product_master_id, quantity, a_qty, b_qty, c_qty, defect_qty, warehouse_id, status, delivery_type, is_reverse, company_id) VALUES ($ordId, $rowA, $master, 30, 30, 0, 0, 0, $finWh, 'DRAFT', 'DELIVERY', 0, $CID)")
  $dvHist = [int](SqlOne "SELECT id FROM outsource_order_delivery WHERE order_id=$ordId ORDER BY id DESC LIMIT 1")
  # simulate an order EDIT: the product rows are deleted and re-created (new row id, same master id)
  SqlExec ("DELETE FROM outsource_order_product WHERE id=$rowA")
  SqlExec ("INSERT INTO outsource_order_product (order_id, product_name, product_id, quantity, company_id) VALUES ($ordId, 'VERIFY-D-P2', $master, 100, $CID)")
  $rowB = [int](SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$ordId ORDER BY id LIMIT 1")
  # (2) the draft we are going to EDIT (attached to the new row). It must be a DIFFERENT record,
  #     because the quantity check correctly excludes the record being edited (excludeId).
  SqlExec ("INSERT INTO outsource_order_delivery (order_id, product_id, product_master_id, quantity, a_qty, b_qty, c_qty, defect_qty, warehouse_id, status, delivery_type, is_reverse, company_id) VALUES ($ordId, $rowB, $master, 20, 20, 0, 0, 0, $finWh, 'DRAFT', 'DELIVERY', 0, $CID)")
  $dvEdit = [int](SqlOne "SELECT id FROM outsource_order_delivery WHERE order_id=$ordId ORDER BY id DESC LIMIT 1")
  Info "fixture: order=$ordId oldRow=$rowA newRow=$rowB master=$master historyDelivery=$dvHist (30) editableDelivery=$dvEdit (20)"
  $diagDv = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,'/',IFNULL(product_id,0),'/',IFNULL(product_master_id,0),'/',IFNULL(quantity,0),'/',status) ORDER BY id) FROM outsource_order_delivery WHERE order_id=$ordId"
  $diagOp = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,'/',IFNULL(product_id,0),'/',IFNULL(quantity,0)) ORDER BY id) FROM outsource_order_product WHERE order_id=$ordId"
  Info ("DB deliveries (id/rowId/masterId/qty/status) = " + $diagDv)
  Info ("DB order-product rows (id/masterId/qty) = " + $diagOp)
  # 30 (history, matched by master) + 80 = 110 > 100 -> must be rejected. Row-id-only matching would
  # miss the 30 (it points at the deleted row A) and let this through.
  $body59 = @{ orderId = [long]$ordId; productId = [long]$rowB; productMasterId = [long]$master
               quantity = 80; aQty = 80; bQty = 0; cQty = 0; defectQty = 0; warehouseId = [long]$finWh }
  $r59 = Req 'PUT' ("/outsource/order-delivery/$dvEdit") $admin $body59
  if ((BCode $r59) -eq 200) { Bad 'edit to 80 was ACCEPTED -> the historical 30 was not counted (F7-59 not fixed)' }
  elseif ((BMsg $r59) -like '*超出订单数量*') { Ok ('rejected: ' + (BMsg $r59) + '  => history matched by master id (F7-59)') }
  else { Bad ('rejected for another reason: ' + (BMsg $r59)) }
  # positive control: a smaller value that fits must still be accepted (no over-blocking)
  $body59b = @{ orderId = [long]$ordId; productId = [long]$rowB; productMasterId = [long]$master
                quantity = 50; aQty = 50; bQty = 0; cQty = 0; defectQty = 0; warehouseId = [long]$finWh }
  $r59b = Req 'PUT' ("/outsource/order-delivery/$dvEdit") $admin $body59b
  if ((BCode $r59b) -eq 200) { Ok 'a fitting value (30+50=80 <= 100) is still accepted (no over-blocking)' }
  else { Bad ('a fitting value was rejected: ' + (BMsg $r59b)) }
} else { Skip 'cannot build the F7-59 fixture' }

# ---------- F7-60 ----------
Write-Output ''
Write-Output '=== F7-60) list page and detail page must agree on "material shortage" ==='
$pageRes = Req 'GET' '/outsource/order/page' $admin $null
$shortageByOrder = @{}
if ((BCode $pageRes) -eq 200 -and $null -ne $pageRes.data -and $null -ne $pageRes.data.records) {
  foreach ($rec in $pageRes.data.records) {
    $k = [long]$rec.id
    $shortageByOrder[$k] = $rec.materialShortage
  }
} else { Skip ('could not read the order list: ' + (BMsg $pageRes)) }
$compared = 0; $mismatch = 0
foreach ($oid in @($shortageByOrder.Keys)) {
  $listVal = $shortageByOrder[$oid]
  if ($null -eq $listVal) { continue }              # FINISHED/CANCELLED -> null, not comparable
  $ms = Req 'GET' ("/outsource/order/$oid/material-stock") $admin $null
  if ((BCode $ms) -ne 200 -or $null -eq $ms.data -or $null -eq $ms.data.materials) { continue }
  $detailShort = $false
  foreach ($m in $ms.data.materials) {
    if ($null -ne $m.shortage -and [double]$m.shortage -gt 0) { $detailShort = $true; break }
  }
  $compared++
  $listBool = [bool]$listVal
  if ($listBool -ne $detailShort) {
    $mismatch++
    Bad ("order ${oid}: list says shortage=" + $listVal + " but detail says " + $detailShort)
  }
}
if ($compared -eq 0) { Skip 'no comparable order (list value null on every row)' }
elseif ($mismatch -eq 0) { Ok ("list page and detail page agree on all $compared comparable orders (F7-60 consistency)") }
Info 'NOTE: an exact per-unit difference cannot be constructed here because the view demand_quantity IS derived from quantity_per_set (ROUND); the assertion therefore checks agreement, which is the goal of F7-60.'

# ---------- F7-77 ----------
Write-Output ''
Write-Output '=== F7-77) one pricing implementation: CANCELLED orders excluded everywhere ==='
$mat77 = 0; $moA = 0; $moB = 0
$mr = Req 'POST' '/outsource/material' $admin @{ materialName = 'VERIFY-D-MAT'; unit = 'PCS' }
if ((BCode $mr) -eq 200 -and $null -ne $mr.data) { $mat77 = [int]$mr.data }
if ($mat77 -gt 0 -and $supId) {
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-D-MA', 'CANCELLED', $supId, 'OUTSOURCE', $CID)")
  $moA = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-D-MA' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moA, $mat77, 100, 0, 999, 99900, $CID)")
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-D-MB', 'RECEIVING', $supId, 'OUTSOURCE', $CID)")
  $moB = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-D-MB' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moB, $mat77, 100, 0, 2, 200, $CID)")

  $wp = Req 'GET' ("/outsource/delivery/material-weighted-price?factoryId=$supId&materialId=$mat77") $admin $null
  $wpv = [double]($wp.data)
  if ([math]::Abs($wpv - 2.0) -lt 0.01) { Ok ("weighted price = $wpv (cancelled @999 excluded)") }
  else { Bad ("weighted price = $wpv (expected 2; ~500 means cancelled orders counted)") }

  $f1 = Req 'GET' ("/outsource/material-return/fifo-price?materialId=$mat77&qty=1") $admin $null
  $f1v = [double]($f1.data)
  if ([math]::Abs($f1v - 2.0) -lt 0.01) { Ok ("material-return FIFO price = $f1v (cancelled @999 excluded)") }
  else { Bad ("material-return FIFO price = $f1v (expected 2; ~500.5 means cancelled orders counted)") }

  $f2 = Req 'GET' ("/outsource/return-order/fifo-price?materialId=$mat77&qty=1") $admin $null
  $f2v = [double]($f2.data)
  if ([math]::Abs($f2v - 2.0) -lt 0.01) { Ok ("return-order FIFO price = $f2v (cancelled @999 excluded)") }
  else { Bad ("return-order FIFO price = $f2v (expected 2; ~500.5 means cancelled orders counted)") }
} else { Skip 'cannot build the F7-77 fixture' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($ordId -gt 0) { SqlExec ("DELETE FROM outsource_order_delivery WHERE order_id=$ordId") }
if ($ordId -gt 0) { SqlExec ("DELETE FROM outsource_order_product WHERE order_id=$ordId") }
if ($ordId -gt 0) { SqlExec ("DELETE FROM outsource_order WHERE id=$ordId") }
if ($moA -gt 0)   { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moA") }
if ($moA -gt 0)   { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moA") }
if ($moB -gt 0)   { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moB") }
if ($moB -gt 0)   { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moB") }
SqlExec "DELETE FROM supplier_material WHERE material_id IN (SELECT id FROM outsource_material WHERE material_name='VERIFY-D-MAT')"
SqlExec "DELETE FROM outsource_material WHERE material_name='VERIFY-D-MAT'"

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_product')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material')
$c5 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$c6 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
Info ("after cleanup: order=$c1(was $cntOrd) orderProduct=$c2(was $cntOp) orderDelivery=$c3(was $cntOd) material=$c4(was $cntMat) mo=$c5(was $cntMo) moi=$c6(was $cntMoi)")
if ($c1 -eq $cntOrd -and $c2 -eq $cntOp -and $c3 -eq $cntOd -and $c4 -eq $cntMat -and $c5 -eq $cntMo -and $c6 -eq $cntMoi) {
  Ok 'all 6 tables back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
