# verify-fix-f7-61-69-71-79.ps1
#
# Regression for the B-batch "guard rails" of the 2026-09-20 outsource fix (report section 45.1):
#   F7-61  outsource order edit must be limited to PENDING (was: only CANCELLED was blocked)
#   F7-69  material repair-return credit-back must be an atomic SQL update (was: read-modify-write)
#   F7-71  material return-refund must not exceed the order's returnable quantity
#   F7-79  finished-goods repair-return must normalise productId (sent vs returned key mismatch)
#
# FIXTURES: created with SQL (company_id included!) and removed at the end, counts self-checked.
# NOTE: the F7-69 fixture also exercises F7-61's fixture row (same order), and both are rolled back
# via the product's own cancel endpoint so stock/quantities return to the pre-run values.
# ASCII-only on purpose EXCEPT the Chinese expectations, so this file NEEDS a UTF-8 BOM.

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
function ExpectReject($label, $r, $marker) {
  if ((BCode $r) -eq 200) { Bad ($label + ': ACCEPTED (expected rejection)'); return }
  if ([string]::IsNullOrWhiteSpace($marker)) { Ok ($label + ': rejected -> ' + (BMsg $r)); return }
  if ((BMsg $r) -like ('*' + $marker + '*')) { Ok ($label + ': rejected by the right rule (' + (BMsg $r) + ')') }
  else { Bad ($label + ': rejected but wrong reason -> ' + (BMsg $r) + ' (expected *' + $marker + '*)') }
}

Write-Output '=== 0) login + baseline ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'
$CID = 1

$cntOrder = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$cntMo    = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$cntMoi   = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$cntRet   = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
$cntRetI  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return_item')
$cntRep   = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return_repair')
$cntRO    = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order')
$cntROR   = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order_repair')
# F7-141（2026-09-20，测试卫生 B2）：warehouse_stock_log 是 append-only 台账，审核/反审核都会新增行；
# 本脚本只还原业务表 ⇒ 反复运行会把测试噪声累积进审计表。记录主键上界，收尾只删**本次运行新增的**。
$slMax0 = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
# F7-297（2026-10-05 审核）：本用例经接口审核/反审核改动库存，而清理段只删掉本次新增的**流水**。
# 原先自检只比对 8 张业务表 + 流水的 max(id)，**完全没有库存断言** ⇒ 只要有一次审核没被对称撤销，
# 就会留下"库存被改、流水被删"的静默差异（正是门禁那条"库存 vs 流水"不变量被测试自身破坏的路径）。
# 这里按 (warehouse, product, material, quality) 维度各取一份**精确快照**（库存侧与流水侧各一份），
# 运行末逐字比对 —— 与 verify-fix-f7-138 的"整行快照 + 双侧回滚"同范式。
function Snap([string]$table, [string]$col) {
  $q = "SELECT IFNULL(GROUP_CONCAT(k SEPARATOR ';'),'') FROM (SELECT CONCAT(warehouse_id,'/',IFNULL(product_id,0),'/',IFNULL(material_id,0),'/',IFNULL(quality_type,'-'),'=',SUM($col)) k FROM $table GROUP BY warehouse_id, product_id, material_id, quality_type ORDER BY warehouse_id, product_id, material_id, quality_type) t"
  return [string](SqlOne $q)
}
$stkMap0 = Snap 'warehouse_stock' 'quantity'
$logMap0 = Snap 'warehouse_stock_log' 'change_quantity'
Info "baseline: order=$cntOrder mo=$cntMo moi=$cntMoi ret=$cntRet retItem=$cntRetI repairRec=$cntRep retOrder=$cntRO roRepair=$cntROR"

# ---------- F7-61 ----------
Write-Output ''
Write-Output '=== F7-61) order edit is limited to PENDING ==='
$prodOrderId = SqlOne "SELECT id FROM outsource_order WHERE status='PRODUCING' ORDER BY id LIMIT 1"
if ($prodOrderId) {
  $body61 = @{ products = @(@{ productName = 'VERIFY-B-P'; quantity = 1; unitPrice = 1 }) }
  ExpectReject 'PUT on a PRODUCING order' (Req 'PUT' ("/outsource/order/$prodOrderId") $admin $body61) '只有待审核的加工单可以编辑'
} else { Skip 'no PRODUCING order in the DB' }

# positive control: a PENDING order must still be editable
# NOTE: the factory must be a supplier of FACTORY type -- take one that an existing order already uses
# (using just "the first supplier" makes assertFactory reject the fixture).
$factoryId = SqlOne "SELECT factory_id FROM outsource_order WHERE factory_id IS NOT NULL ORDER BY id LIMIT 1"
$planOrderId = 0
if ($factoryId) {
  SqlExec ("INSERT INTO outsource_order (code, factory_id, status, company_id) VALUES ('VERIFY-B-ORDER', $factoryId, 'PENDING', $CID)")
  $planOrderId = [int](SqlOne "SELECT id FROM outsource_order WHERE code='VERIFY-B-ORDER' ORDER BY id DESC LIMIT 1")
  if ($planOrderId -gt 0) {
    $r61 = Req 'PUT' ("/outsource/order/$planOrderId") $admin $body61
    if ((BCode $r61) -eq 200) { Ok 'PUT on a PENDING order still accepted (no over-blocking)' }
    else { Bad ('PUT on a PENDING order rejected: ' + (BMsg $r61)) }
  } else { Skip 'could not create the PENDING fixture order' }
} else { Skip 'no supplier row for the PENDING fixture' }

# ---------- fixtures shared by F7-69 / F7-71 ----------
Write-Output ''
Write-Output '=== fixtures for F7-69 / F7-71 ==='
$supplierId = SqlOne "SELECT id FROM supplier ORDER BY id LIMIT 1"
$materialId = SqlOne "SELECT id FROM outsource_material ORDER BY id LIMIT 1"
$whId       = SqlOne "SELECT id FROM warehouse WHERE status=1 ORDER BY id LIMIT 1"
$moId = 0; $moiId = 0; $retRepairId = 0; $retRefundId = 0
if ($supplierId -and $materialId -and $whId) {
  # 2026-09-28（三态口径）：退货退款必须挂**已结单**订单（未结单只能用「订单退料」）⇒ 夹具用 FINISHED，
  #   否则 F7-71 的审核会先被"该物料订单未结单：请改用订单退料"拦下，测不到"不超可退数量"这道护栏。
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-B-MO', 'FINISHED', $supplierId, 'OUTSOURCE', $CID)")
  $moId = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-B-MO' ORDER BY id DESC LIMIT 1")
  # received=5, repairing=3  ->  returnable (for refund) = 5 - 0 - 3 - 0 = 2
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, repair_returned_qty, unit_price, amount, company_id) VALUES ($moId, $materialId, 100, 5, 3, 1, 100, $CID)")
  $moiId = [int](SqlOne "SELECT id FROM outsource_material_order_item WHERE order_id=$moId ORDER BY id LIMIT 1")
  # REPAIR return order: AUDITED + linked to the order + already deducted (frozen)
  SqlExec ("INSERT INTO outsource_material_return (code, return_type, supplier_id, from_warehouse_id, return_date, status, material_order_id, deducted_flag, company_id) VALUES ('VERIFY-B-R1', 'REPAIR', $supplierId, $whId, CURDATE(), 'AUDITED', $moId, 1, $CID)")
  $retRepairId = [int](SqlOne "SELECT id FROM outsource_material_return WHERE code='VERIFY-B-R1' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_return_item (return_order_id, outsource_material_id, quantity, material_order_item_id, unit_price, amount, company_id) VALUES ($retRepairId, $materialId, 3, $moiId, 1, 3, $CID)")
  # REFUND return order: DRAFT + linked + a clearly excessive quantity (99 > returnable 2)
  SqlExec ("INSERT INTO outsource_material_return (code, return_type, supplier_id, from_warehouse_id, return_date, status, material_order_id, company_id) VALUES ('VERIFY-B-R2', 'REFUND', $supplierId, $whId, CURDATE(), 'DRAFT', $moId, $CID)")
  $retRefundId = [int](SqlOne "SELECT id FROM outsource_material_return WHERE code='VERIFY-B-R2' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_return_item (return_order_id, outsource_material_id, quantity, unit_price, amount, company_id) VALUES ($retRefundId, $materialId, 99, 1, 99, $CID)")
  Info "fixtures: mo=$moId moi=$moiId repairReturn=$retRepairId refundReturn=$retRefundId (mat=$materialId wh=$whId)"
} else { Skip 'missing supplier/material/warehouse for the fixtures' }

# ---------- F7-69 ----------
Write-Output ''
Write-Output '=== F7-69) repair-return credit-back (atomic) ==='
if ($retRepairId -gt 0 -and $moiId -gt 0) {
  $body69 = @{ warehouseId = [long]$whId; items = @(@{ materialId = [long]$materialId; quantity = 2 }) }
  $r69 = Req 'POST' ("/outsource/material-return/$retRepairId/repair-return") $admin $body69
  if ((BCode $r69) -ne 200) { Bad ('repair-return failed: ' + (BMsg $r69)) }
  # 2026-09-28（用户口径「登记返回需要审核和反审核」）：登记只建**草稿** ⇒ 回补订单收料数搬到**审核**，
  # 断言相应拆成"草稿不动账 → 审核落账 → 反审核回滚 → 删草稿"四步（原子性断言不变）。
  $repRecId = SqlOne "SELECT id FROM outsource_material_return_repair WHERE return_order_id=$retRepairId ORDER BY id DESC LIMIT 1"
  if ($repRecId) {
    $diagDraft = SqlOne "SELECT CONCAT(IFNULL(received_quantity,0),'/',IFNULL(repair_returned_qty,0)) FROM outsource_material_order_item WHERE id=$moiId"
    Info ("after register (draft): received/repairing = " + $diagDraft + "  (expected 5/3 - nothing applied yet)")
    if ($diagDraft -eq '5/3') { Ok 'registration (draft) did NOT touch the order quantities' }
    else { Bad ('a DRAFT moved the order quantities: ' + $diagDraft) }

    $ra69 = Req 'PUT' ("/outsource/material-return/repair-return/$repRecId/audit") $admin $null
    $diag = SqlOne "SELECT CONCAT(IFNULL(received_quantity,0),'/',IFNULL(repair_returned_qty,0)) FROM outsource_material_order_item WHERE id=$moiId"
    Info ("after audit: received/repairing = " + $diag + "  (expected 7/1)  auditCode=" + (BCode $ra69))
    if ((BCode $ra69) -eq 200 -and $diag -eq '7/1') { Ok 'credit-back applied correctly at AUDIT (+2 received, -2 repairing) via the atomic SQL path' }
    else { Bad ('credit-back produced ' + $diag + ' (expected 7/1, audit code ' + (BCode $ra69) + ')') }

    # roll back through the product's own un-audit endpoint -> quantities must return to 5/3, then drop the draft
    $ru69 = Req 'PUT' ("/outsource/material-return/repair-return/$repRecId/un-audit") $admin $null
    $diag2 = SqlOne "SELECT CONCAT(IFNULL(received_quantity,0),'/',IFNULL(repair_returned_qty,0)) FROM outsource_material_order_item WHERE id=$moiId"
    Info ("after un-audit: received/repairing = " + $diag2 + "  (expected 5/3)  unAuditCode=" + (BCode $ru69))
    if ((BCode $ru69) -eq 200 -and $diag2 -eq '5/3') { Ok 'un-audit rolled the credit-back back to 5/3 (fixture restored)' }
    else { Bad ('un-audit did not restore the fixture: code=' + (BCode $ru69) + ' values=' + $diag2) }
    $rc = Req 'DELETE' ("/outsource/material-return/repair-return/$repRecId") $admin $null
    if ((BCode $rc) -eq 200) { Ok 'the draft was deleted (delete is draft-only, repeatable)' }
    else { Bad ('deleting the draft failed: ' + (BMsg $rc)) }
  } else { Bad 'repair record not found (cannot verify the audit path)' }
} else { Skip 'no F7-69 fixture' }

# ---------- F7-71 ----------
Write-Output ''
Write-Output '=== F7-71) refund must not exceed the returnable quantity ==='
if ($retRefundId -gt 0) {
  $r71 = Req 'PUT' ("/outsource/material-return/$retRefundId/audit") $admin $null
  ExpectReject 'PUT audit of an over-quantity REFUND' $r71 '可退数量'
  $st = SqlOne "SELECT status FROM outsource_material_return WHERE id=$retRefundId"
  if ($st -eq 'DRAFT') { Ok ('the REFUND order stayed DRAFT (no partial posting, status=' + $st + ')') }
  else { Bad ('the REFUND order status changed to ' + $st + ' despite the rejection') }
} else { Skip 'no F7-71 fixture' }

# ---------- F7-79 ----------
Write-Output ''
Write-Output '=== F7-79) finished-goods repair-return normalises productId ==='
# Fixture: a REPAIR return order whose product line stores the ORDER-PRODUCT ROW id (81) instead of the
# master product id (62) -- exactly the historical/front-end shape that made "sent" and "returned" keys
# disagree. Without normalisation the call fails with "该产品不在本单送修范围内"; with it the call must
# succeed AND persist the MASTER id.
$rtId = 0
$rowId79  = SqlOne "SELECT id FROM outsource_order_product ORDER BY id LIMIT 1"
$master79 = SqlOne "SELECT product_id FROM outsource_order_product WHERE id=$rowId79"
$ordId79  = SqlOne "SELECT order_id FROM outsource_order_product WHERE id=$rowId79"
$wh79     = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND status=1 ORDER BY id LIMIT 1"
if ($rowId79 -and $master79 -and $ordId79 -and $wh79) {
  SqlExec ("INSERT INTO outsource_return_order (code, return_type, status, order_id, closed_flag, company_id) VALUES ('VERIFY-B-RT', 'REPAIR', 'AUDITED', $ordId79, 0, $CID)")
  $rtId = [int](SqlOne "SELECT id FROM outsource_return_order WHERE code='VERIFY-B-RT' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_return_order_product (return_order_id, product_id, product_name, quantity, company_id, quality_type) VALUES ($rtId, $rowId79, 'VERIFY-B', 5, $CID, 'A')")
  Info ("fixture: returnOrder=$rtId order=$ordId79 rowId(stored)=$rowId79 masterId=$master79 wh=$wh79")
  $body79 = @{ warehouseId = [long]$wh79; items = @(@{ productId = [long]$rowId79; quantity = 2 }) }
  $r79 = Req 'POST' ("/outsource/return-order/$rtId/repair-return") $admin $body79
  if ((BCode $r79) -ne 200) {
    if ((BMsg $r79) -like '*不在本单送修范围内*') { Bad 'line-id payload was NOT normalised (F7-79 not fixed): ' + (BMsg $r79) }
    else { Bad ('repair-return failed for another reason: ' + (BMsg $r79)) }
  } else {
    $storedProduct = SqlOne "SELECT product_id FROM outsource_return_order_repair WHERE return_order_id=$rtId ORDER BY id DESC LIMIT 1"
    if ([long]$storedProduct -eq [long]$master79) {
      Ok ("normalised: input row id $rowId79 was persisted as MASTER id $storedProduct - F7-79 fixed")
    } else { Bad ("persisted product_id=$storedProduct (expected master $master79) - F7-79 not fixed") }
    $rec79 = SqlOne "SELECT id FROM outsource_return_order_repair WHERE return_order_id=$rtId ORDER BY id DESC LIMIT 1"
    if ($rec79) {
      $rc79 = Req 'DELETE' ("/outsource/return-order/repair-return/$rec79") $admin $null
      if ((BCode $rc79) -eq 200) { Ok 'cancelled the probe repair-return (stock rolled back by the endpoint)' }
      else { Bad ('could not cancel the probe repair-return: ' + (BMsg $rc79)) }
    }
  }
} else { Skip 'cannot build the F7-79 fixture (no order-product row / inventory warehouse)' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($retRepairId -gt 0) { SqlExec ("DELETE FROM outsource_material_return_item WHERE return_order_id=$retRepairId") }
if ($retRefundId -gt 0) { SqlExec ("DELETE FROM outsource_material_return_item WHERE return_order_id=$retRefundId") }
if ($retRepairId -gt 0) { SqlExec ("DELETE FROM outsource_material_return_repair WHERE return_order_id=$retRepairId") }
if ($retRepairId -gt 0) { SqlExec ("DELETE FROM outsource_material_return WHERE id=$retRepairId") }
if ($retRefundId -gt 0) { SqlExec ("DELETE FROM outsource_material_return WHERE id=$retRefundId") }
if ($moId -gt 0)        { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moId") }
if ($moId -gt 0)        { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moId") }
if ($planOrderId -gt 0) { SqlExec ("DELETE FROM outsource_order_product WHERE order_id=$planOrderId") }
if ($planOrderId -gt 0) { SqlExec ("DELETE FROM outsource_order WHERE id=$planOrderId") }
if ($rtId -gt 0)        { SqlExec ("DELETE FROM outsource_return_order_repair WHERE return_order_id=$rtId") }
if ($rtId -gt 0)        { SqlExec ("DELETE FROM outsource_return_order_product WHERE return_order_id=$rtId") }
if ($rtId -gt 0)        { SqlExec ("DELETE FROM outsource_return_order WHERE id=$rtId") }
# F7-141：只删本次运行新增的流水（历史审计行不动）
SqlExec ("DELETE FROM warehouse_stock_log WHERE id > $slMax0")

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_order')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
$c5 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return_item')
$c6 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return_repair')
$c7 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order')
$c8 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order_repair')
$slNow = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
Info ("after cleanup: order=$c1(was $cntOrder) mo=$c2(was $cntMo) moi=$c3(was $cntMoi) ret=$c4(was $cntRet) retItem=$c5(was $cntRetI) repairRec=$c6(was $cntRep) retOrder=$c7(was $cntRO) roRepair=$c8(was $cntROR) stockLogMax=$slNow(was $slMax0)")
if ($c1 -eq $cntOrder -and $c2 -eq $cntMo -and $c3 -eq $cntMoi -and $c4 -eq $cntRet -and $c5 -eq $cntRetI -and $c6 -eq $cntRep `
    -and $c7 -eq $cntRO -and $c8 -eq $cntROR -and $slNow -le $slMax0) {
  Ok 'all 8 tables + this run''s stock-log rows back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

# F7-297：库存 / 流水两侧的维度快照必须与运行前逐字一致（见本文件上方 Snap 的说明）
$stkMap1 = Snap 'warehouse_stock' 'quantity'
$logMap1 = Snap 'warehouse_stock_log' 'change_quantity'
if ($stkMap1 -eq $stkMap0 -and $logMap1 -eq $logMap0) {
  Ok 'stock AND stock-log per-dimension snapshots match the pre-run baseline (F7-297)'
} else {
  Bad 'stock/stock-log per-dimension snapshots DRIFTED from the pre-run baseline (F7-297) -- the fixture changed stock without a paired log row'
}

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
