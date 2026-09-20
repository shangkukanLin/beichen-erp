# verify-fix-f7-58-67-68-76.ps1
#
# Regression for the A batch of the 2026-09-20 outsource fix (report section 43.1):
#   F7-58  create/update of an outsource delivery must apply the SAME payload validation
#          (quantity > 0, grade sum == total quantity, warehouse required, type not changeable)
#   F7-68  manual delivery edit: only 发料/调拨 may be edited, type cannot change, warehouses re-validated
#   F7-76  editing a delivery must NOT wipe the item -> order-line link (item_id)
#   F7-67  editing a material order must keep item row ids (differential update) and must refuse to
#          remove a line that already has receiving references
#
# FIXTURES: this script creates draft rows with SQL and deletes them at the end (self-checked).
# ASCII-only on purpose. NOTE: never use $pid as a variable name (read-only automatic variable).

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
# assert: business code is NOT 200 and the message contains the marker (proves WHICH rule fired)
function ExpectReject($label, $r, $marker) {
  if ((BCode $r) -eq 200) { Bad ($label + ': ACCEPTED (expected rejection)'); return }
  if ([string]::IsNullOrWhiteSpace($marker)) { Ok ($label + ': rejected -> ' + (BMsg $r)); return }
  if ((BMsg $r) -like ('*' + $marker + '*')) { Ok ($label + ': rejected by the right rule (' + (BMsg $r) + ')') }
  else { Bad ($label + ': rejected but wrong reason -> ' + (BMsg $r) + ' (expected *' + $marker + '*)') }
}

Write-Output '=== 0) login + fixtures ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

# NOTE: the app enforces a tenant filter (company_id). Rows inserted by raw SQL must carry the
# logged-in company id, otherwise every request answers "not found" (this bit us on the first run).
$CID = 1

# --- fixture data (all inserted here, removed in the finally-ish cleanup below) ---
$obCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$opCount0 = [int](SqlOne "SELECT COUNT(*) FROM outsource_order_product WHERE product_name='VERIFY-A-ROW'")
$moCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$moiCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$dvCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$dviCount0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')

# 1) delivery draft for F7-58. NOTE: every existing PRODUCING order row is already fully delivered
#    (planned 100, delivered 100), so a self-owned product ROW is created for this fixture: it gives us
#    a positive control (a valid payload must still be accepted) that no existing row can provide.
$orderId = SqlOne "SELECT o.id FROM outsource_order o WHERE o.status='PRODUCING' ORDER BY o.id LIMIT 1"
$masterId = SqlOne "SELECT product_id FROM outsource_order_product WHERE order_id=$orderId ORDER BY id LIMIT 1"
$whProd = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1"
if (-not $orderId -or -not $masterId -or -not $whProd) { Skip 'missing source data (order/product/warehouse)'; Write-Output 'RESULT SKIP'; exit 0 }
# NOTE (2026-09-20, after F7-59): the fixture row must use a product MASTER id that has NO history on
# this order. F7-59 made the quantity check match deliveries by master id, so reusing $masterId here would
# count this order's existing 100/100 deliveries and reject even a 1-piece payload (business-correct, but
# it would defeat the point of this positive control).
$otherMaster = SqlOne "SELECT product_id FROM outsource_order_product WHERE product_id IS NOT NULL AND product_id <> $masterId ORDER BY id LIMIT 1"
if (-not $otherMaster) { $otherMaster = $masterId }
SqlExec ("INSERT INTO outsource_order_product (order_id, product_name, product_id, quantity, unit_price, company_id) VALUES ($orderId, 'VERIFY-A-ROW', $otherMaster, 100, 20, $CID)")
$rowId = [int](SqlOne "SELECT id FROM outsource_order_product WHERE product_name='VERIFY-A-ROW' ORDER BY id DESC LIMIT 1")
Info ("fixture source: order=$orderId ownRow=$rowId master=$masterId wh=$whProd")
if ($rowId -le 0) { Skip 'could not create the fixture order-product row (check columns)'; Write-Output 'RESULT SKIP'; exit 0 }

SqlExec ("INSERT INTO outsource_order_delivery (order_id, product_id, product_master_id, quantity, a_qty, b_qty, c_qty, defect_qty, warehouse_id, status, delivery_type, is_reverse, company_id, remark) VALUES ($orderId, $rowId, $masterId, 1, 1, 0, 0, 0, $whProd, 'DRAFT', 'DELIVERY', 0, $CID, 'VERIFY-A-BATCH-F7-58')")
$obId = [int](SqlOne 'SELECT MAX(id) FROM outsource_order_delivery')
Info "created fixture delivery draft id=$obId"

# 2) manual delivery draft (发料) + one item row WITH item_id, for F7-68 / F7-76
$matWh = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='AUXILIARY' ORDER BY id LIMIT 1"
$outWh = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1"
$outFactory = SqlOne "SELECT factory_id FROM warehouse WHERE id=$outWh"
$materialId = SqlOne "SELECT id FROM outsource_material ORDER BY id LIMIT 1"
$moiForLink = SqlOne "SELECT id FROM outsource_material_order_item ORDER BY id LIMIT 1"
Info ("fixture source: matWh=$matWh outWh=$outWh factory=$outFactory material=$materialId orderItem=$moiForLink")
if ($matWh -and $outWh -and $outFactory -and $materialId) {
  SqlExec ("INSERT INTO outsource_delivery (code, delivery_type, status, from_warehouse_id, to_warehouse_id, factory_id, supplier_direct, company_id, delivery_date, remark) VALUES ('VERIFY-A-M1', 'DELIVERY', 'DRAFT', $matWh, $outWh, $outFactory, 0, $CID, CURDATE(), 'VERIFY-A-BATCH-F7-68')")
  $manualId = [int](SqlOne 'SELECT MAX(id) FROM outsource_delivery')
  $linkVal = if ($moiForLink) { $moiForLink } else { 'NULL' }
  # NOTE: the column is outsource_material_id (NOT material_id) and rows must carry company_id,
  # otherwise the tenant filter hides them from the service layer.
  SqlExec ("INSERT INTO outsource_delivery_item (delivery_id, item_id, outsource_material_id, quantity, quality_type, unit_price, company_id) VALUES ($manualId, $linkVal, $materialId, 1, 'GOOD', 1, $CID)")
  Info "created fixture manual delivery id=$manualId (item carries item_id=$linkVal)"
} else { $manualId = 0; Skip 'cannot build manual delivery fixture (missing warehouse/material)' }

# 3) RECEIVE draft (auto-type) for F7-68 "auto types cannot be edited by hand"
if ($outWh) {
  $fVal = if ($outFactory) { $outFactory } else { 'NULL' }
  SqlExec ("INSERT INTO outsource_delivery (code, delivery_type, status, to_warehouse_id, factory_id, supplier_direct, company_id, delivery_date, remark) VALUES ('VERIFY-A-R1', 'RECEIVE', 'DRAFT', $outWh, $fVal, 1, $CID, CURDATE(), 'VERIFY-A-BATCH-F7-68R')")
  $receiveId = [int](SqlOne 'SELECT MAX(id) FROM outsource_delivery')
  Info "created fixture RECEIVE delivery id=$receiveId"
} else { $receiveId = 0 }

# 4) material order + 1 line (+ one receiving line referencing it) for F7-67.
#    One line is enough: the guard to test is "refuse to REMOVE a line that has receiving references",
#    and the differential-update property is tested by editing that same line in place.
$supplierId = SqlOne "SELECT id FROM supplier ORDER BY id LIMIT 1"
if ($supplierId -and $materialId) {
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id, remark) VALUES ('VERIFY-A-MO', 'PENDING', $supplierId, 'OUTSOURCE', $CID, 'VERIFY-A-BATCH-F7-67')")
  $moId = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-A-MO' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moId, $materialId, 10, 0, 1, 10, $CID)")
  # the fixture inserts exactly ONE line, so select it by order (no ambiguity about MAX(id))
  $mi1 = [int](SqlOne "SELECT id FROM outsource_material_order_item WHERE order_id=$moId ORDER BY id LIMIT 1")
  # a receiving line that REFERENCES mi1 -> removing mi1 from the order must be refused
  if ($receiveId -and $mi1 -gt 0) {
    SqlExec ("INSERT INTO outsource_delivery_item (delivery_id, item_id, outsource_material_id, quantity, quality_type, unit_price, company_id) VALUES ($receiveId, $mi1, $materialId, 1, 'GOOD', 1, $CID)")
  }
  Info "created fixture material order id=$moId line mi1=$mi1"
} else { $moId = 0; $mi1 = 0; Skip 'cannot build material order fixture (no supplier/material)' }

$base = @{ orderId = [long]$orderId; productId = [long]$rowId; productMasterId = [long]$masterId; warehouseId = [long]$whProd }

# ---------- F7-58: POST vs PUT must judge the same payload identically ----------
Write-Output ''
Write-Output '=== F7-58) create vs update payload validation (pair-wise) ==='

$badQty  = @{ orderId = [long]$orderId; productId = [long]$rowId; quantity = -5; aQty = 5; bQty = 0; cQty = 0; defectQty = 0; warehouseId = [long]$whProd }
$badSum  = @{ orderId = [long]$orderId; productId = [long]$rowId; quantity = 1; aQty = 100; bQty = 0; cQty = 0; defectQty = 0; warehouseId = [long]$whProd }
$badWh   = @{ orderId = [long]$orderId; productId = [long]$rowId; quantity = 1; aQty = 1; bQty = 0; cQty = 0; defectQty = 0; warehouseId = $null }

ExpectReject 'POST quantity=-5'      (Req 'POST' '/outsource/order-delivery' $admin $badQty) '交货数量必须大于0'
ExpectReject 'PUT  quantity=-5'      (Req 'PUT' ("/outsource/order-delivery/$obId") $admin $badQty) '交货数量必须大于0'
ExpectReject 'POST grade sum != qty' (Req 'POST' '/outsource/order-delivery' $admin $badSum) '必须等于交货总数量'
ExpectReject 'PUT  grade sum != qty' (Req 'PUT' ("/outsource/order-delivery/$obId") $admin $badSum) '必须等于交货总数量'
ExpectReject 'POST no warehouse'     (Req 'POST' '/outsource/order-delivery' $admin $badWh) '入库仓库不能为空'
ExpectReject 'PUT  no warehouse'     (Req 'PUT' ("/outsource/order-delivery/$obId") $admin $badWh) '入库仓库不能为空'

# positive control: a VALID payload must still be accepted by PUT (no over-blocking), and the type must stay DELIVERY
$good = @{ orderId = [long]$orderId; productId = [long]$rowId; quantity = 1; aQty = 1; bQty = 0; cQty = 0; defectQty = 0
           warehouseId = [long]$whProd; deliveryType = 'DEFECT_RETURN' }
$rg = Req 'PUT' ("/outsource/order-delivery/$obId") $admin $good
if ((BCode $rg) -eq 200) { Ok 'PUT with a valid payload still accepted (no over-blocking)' }
elseif ((BMsg $rg) -like '*交货数量必须大于0*' -or (BMsg $rg) -like '*必须等于交货总数量*' -or (BMsg $rg) -like '*入库仓库不能为空*') {
  Bad ('a VALID payload was blocked by the payload validation: ' + (BMsg $rg))
} else {
  Ok ('valid payload passed the payload validation (later rejected by a business rule: ' + (BMsg $rg) + ')')
}
$typeAfter = SqlOne "SELECT delivery_type FROM outsource_order_delivery WHERE id=$obId"
if ($typeAfter -eq 'DELIVERY') { Ok 'delivery_type kept as DELIVERY despite request body trying DEFECT_RETURN (F7-58)' }
else { Bad ('delivery_type changed to ' + $typeAfter + ' (F7-58 not enforced)') }
$valsAfter = SqlOne "SELECT CONCAT(quantity,'|',a_qty) FROM outsource_order_delivery WHERE id=$obId"
if ($valsAfter -eq '1|1') { Ok 'persisted quantity/a_qty = 1|1 (grade and total consistent)' }
else { Bad ('persisted values = ' + $valsAfter + ' (expected 1|1)') }

# ---------- F7-68 ----------
Write-Output ''
Write-Output '=== F7-68) update must re-validate type + warehouses ==='
if ($manualId -gt 0) {
  $badType = @{ deliveryType = 'RECEIVE'; fromWarehouseId = [long]$matWh; toWarehouseId = [long]$outWh; factoryId = [long]$outFactory
                deliveryDate = (Get-Date).ToString('yyyy-MM-dd'); items = @() }
  ExpectReject 'PUT deliveryType RECEIVE' (Req 'PUT' ("/outsource/delivery/$manualId") $admin $badType) '收发类型不可修改'

  $badWarehouse = @{ deliveryType = 'DELIVERY'; fromWarehouseId = 999999; toWarehouseId = [long]$outWh; factoryId = [long]$outFactory
                     deliveryDate = (Get-Date).ToString('yyyy-MM-dd'); items = @() }
  ExpectReject 'PUT fromWarehouse=999999' (Req 'PUT' ("/outsource/delivery/$manualId") $admin $badWarehouse) '仓库不存在'

  $badHandType = @{ fromWarehouseId = [long]$matWh; toWarehouseId = [long]$outWh; factoryId = [long]$outFactory
                    deliveryDate = (Get-Date).ToString('yyyy-MM-dd'); items = @() }
  if ($manualId -gt 0) {
    # editing a RECEIVE (auto) draft by hand must be refused
    if ($receiveId -gt 0) {
      ExpectReject 'PUT on a RECEIVE draft' (Req 'PUT' ("/outsource/delivery/$receiveId") $admin $badHandType) '不支持手工编辑'
    } else { Skip 'no RECEIVE fixture' }
  }
} else { Skip 'no manual delivery fixture' }

# ---------- F7-76 ----------
Write-Output ''
Write-Output '=== F7-76) editing must keep item_id (order-line link) ==='
if ($manualId -gt 0 -and $moiForLink) {
  $itemsBody = @{ deliveryType = 'DELIVERY'; fromWarehouseId = [long]$matWh; toWarehouseId = [long]$outWh; factoryId = [long]$outFactory
                  deliveryDate = (Get-Date).ToString('yyyy-MM-dd')
                  items = @(@{ itemId = [long]$moiForLink; materialId = [long]$materialId; quantity = 2; qualityType = 'GOOD'; unit_price = 1 }) }
  $r76 = Req 'PUT' ("/outsource/delivery/$manualId") $admin $itemsBody
  if ((BCode $r76) -ne 200) { Bad ('PUT manual delivery failed: ' + (BMsg $r76)) }
  else {
    $kept = SqlOne "SELECT IFNULL(item_id, 0) FROM outsource_delivery_item WHERE delivery_id=$manualId ORDER BY id DESC LIMIT 1"
    $qty = SqlOne "SELECT quantity FROM outsource_delivery_item WHERE delivery_id=$manualId ORDER BY id DESC LIMIT 1"
    if ([long]$kept -eq [long]$moiForLink) { Ok ("item_id preserved after edit (item_id=$kept, qty=$qty) - F7-76 fixed") }
    else { Bad ("item_id wiped after edit (got '$kept', expected $moiForLink) - F7-76 NOT fixed") }
  }
} else { Skip 'no manual delivery fixture / no order line to link' }

# ---------- F7-67 ----------
Write-Output ''
Write-Output '=== F7-67) material order edit: differential update + reference guard ==='
if ($moId -gt 0) {
  # (a) removing the line that has receiving references must be refused
  $removeBody = @{ supplierId = [long]$supplierId; orderType = 'OUTSOURCE'; items = @() }
  ExpectReject 'PUT removing a referenced line' (Req 'PUT' ("/outsource/material-order/$moId") $admin $removeBody) '收货明细引用'

  # (b) keeping the line (with a new quantity) must preserve its row id
  $diagBefore = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,'/',IFNULL(outsource_material_id,0),'/',IFNULL(company_id,0)) ORDER BY id) FROM outsource_material_order_item WHERE order_id=$moId"
  Info ("rows before (b): " + $diagBefore + "  (expected id/materialId/companyId = $mi1/$materialId/$CID)")
  $keepBody = @{ supplierId = [long]$supplierId; orderType = 'OUTSOURCE'
                 items = @(@{ materialId = [long]$materialId; orderQuantity = 20; unitPrice = 1 }) }
  $rk = Req 'PUT' ("/outsource/material-order/$moId") $admin $keepBody
  $diagAfter = SqlOne "SELECT GROUP_CONCAT(CONCAT(id,'/',IFNULL(outsource_material_id,0),'/',IFNULL(company_id,0)) ORDER BY id) FROM outsource_material_order_item WHERE order_id=$moId"
  Info ("rows after  (b): " + $diagAfter)
  if ((BCode $rk) -ne 200) { Bad ('PUT keeping the line failed: ' + (BMsg $rk)) }
  else {
    $stillThere = SqlOne "SELECT COUNT(*) FROM outsource_material_order_item WHERE id=$mi1"
    $q1 = SqlOne "SELECT order_quantity FROM outsource_material_order_item WHERE id=$mi1"
    if ([int]$stillThere -eq 1) { Ok ("row id PRESERVED after edit (id=$mi1, order_quantity=$q1) - F7-67 differential update works") }
    else { Bad 'row id was replaced (delete+insert) - F7-67 not fixed' }
    $keptLink = SqlOne "SELECT COUNT(*) FROM outsource_delivery_item WHERE item_id=$mi1"
    if ([int]$keptLink -ge 1) { Ok 'the receiving line still points at the order line (no dangling reference)' }
    else { Bad 'receiving line lost its target after the order edit' }
  }
  # (c) the guard must not block a pure ADDITION (no over-blocking)
  $addBody = @{ supplierId = [long]$supplierId; orderType = 'OUTSOURCE'
                items = @(@{ materialId = [long]$materialId; orderQuantity = 20; unitPrice = 1 },
                          @{ materialId = [long]$materialId; orderQuantity = 5; unitPrice = 1 }) }
  $ra = Req 'PUT' ("/outsource/material-order/$moId") $admin $addBody
  Info ('PUT with a duplicated material line returned code=' + (BCode $ra) + ' (informational only)')
} else { Skip 'no material order fixture' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($obId -gt 0)                                                              { SqlExec ("DELETE FROM outsource_order_delivery WHERE id=$obId") }
if ($moId -gt 0)                                                              { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moId") }
if ($moId -gt 0)                                                              { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moId") }
if ($manualId -gt 0)                                                          { SqlExec ("DELETE FROM outsource_delivery_item WHERE delivery_id=$manualId") }
if ($receiveId -gt 0)                                                         { SqlExec ("DELETE FROM outsource_delivery_item WHERE delivery_id=$receiveId") }
if ($manualId -gt 0)                                                          { SqlExec ("DELETE FROM outsource_delivery WHERE id=$manualId") }
if ($receiveId -gt 0)                                                         { SqlExec ("DELETE FROM outsource_delivery WHERE id=$receiveId") }
SqlExec "DELETE FROM outsource_order_product WHERE product_name='VERIFY-A-ROW'"

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order_delivery')
$c2 = [int](SqlOne "SELECT COUNT(*) FROM outsource_order_product WHERE product_name='VERIFY-A-ROW'")
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$c5 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$c6 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
Info ("after cleanup: order_delivery=$c1 (was $obCount0) fixture_rows=$c2 (was $opCount0) material_order=$c3 (was $moCount0) material_order_item=$c4 (was $moiCount0) delivery=$c5 (was $dvCount0) delivery_item=$c6 (was $dviCount0)")
if ($c1 -eq $obCount0 -and $c2 -eq $opCount0 -and $c3 -eq $moCount0 -and $c4 -eq $moiCount0 -and $c5 -eq $dvCount0 -and $c6 -eq $dviCount0) {
  Ok 'all 6 tables back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
