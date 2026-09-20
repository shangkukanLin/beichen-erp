# verify-fix-f7-70-72-73-78.ps1
#
# Regression for the B-batch "structure" step of the 2026-09-20 outsource fix (report section 46.1):
#   F7-70  material master-data writes moved into a @Transactional service; delete refuses when referenced
#   F7-72  receive de-duplication must also cover "the first draft was already AUDITED"
#   F7-73  weighted price must exclude CANCELLED orders (and no longer be N+1)
#   F7-78  return-order audit must fail loudly when the factory has no outsource warehouse
#
# FIXTURES: created via SQL + the product's own endpoints, removed at the end, counts self-checked.
# ASCII-only except the Chinese expectations => this file NEEDS a UTF-8 BOM.

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

$cntMat = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material')
$cntMo  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$cntMoi = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$cntDv  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$cntDvi = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
$cntRo  = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order')
Info "baseline: material=$cntMat mo=$cntMo moi=$cntMoi delivery=$cntDv deliveryItem=$cntDvi retOrder=$cntRo"

# ---------- F7-70 ----------
Write-Output ''
Write-Output '=== F7-70) material delete refuses when referenced (+ write path in a service) ==='
$existingMat = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
if ($existingMat) {
  ExpectReject 'DELETE a referenced material' (Req 'DELETE' ("/outsource/material/$existingMat") $admin $null) '不能删除'
} else { Skip 'no existing material to test the reference guard' }

# positive control: a brand-new material has no references -> create then delete must both work
$newMatRes = Req 'POST' '/outsource/material' $admin @{ materialName = 'VERIFY-C-MAT'; unit = 'PCS' }
$newMatId = 0
if ((BCode $newMatRes) -eq 200 -and $null -ne $newMatRes.data) { $newMatId = [int]$newMatRes.data }
if ($newMatId -gt 0) {
  Ok ("POST /outsource/material created id=$newMatId (write path via the new service works)")
  $rd = Req 'DELETE' ("/outsource/material/$newMatId") $admin $null
  if ((BCode $rd) -eq 200) { Ok 'DELETE of an unreferenced material still allowed (no over-blocking)' }
  else { Bad ('DELETE of an unreferenced material rejected: ' + (BMsg $rd)) }
  if ($newMatId -gt 0) { SqlExec ("DELETE FROM outsource_material WHERE id=$newMatId") }
} else { Bad ('POST /outsource/material failed: ' + (BMsg $newMatRes)) }

# ---------- F7-72 ----------
Write-Output ''
Write-Output '=== F7-72) receive de-dup also covers an already-AUDITED delivery ==='
$supId = SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1'
$whId  = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1"
$matId = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
$moId = 0; $moiId = 0; $auditedId = 0
if ($supId -and $whId -and $matId) {
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-C-MO', 'RECEIVING', $supId, 'OUTSOURCE', $CID)")
  $moId = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-C-MO' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moId, $matId, 100, 0, 1, 100, $CID)")
  $moiId = [int](SqlOne "SELECT id FROM outsource_material_order_item WHERE order_id=$moId ORDER BY id LIMIT 1")
  # an ALREADY AUDITED receive delivery with lines that are identical to what we will re-submit
  SqlExec ("INSERT INTO outsource_delivery (code, delivery_type, status, source_order_id, to_warehouse_id, factory_id, supplier_direct, supplier_id, delivery_date, company_id) VALUES ('VERIFY-C-DV', 'RECEIVE', 'AUDITED', $moId, $whId, $supId, 1, $supId, CURDATE(), $CID)")
  $auditedId = [int](SqlOne "SELECT id FROM outsource_delivery WHERE code='VERIFY-C-DV' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_delivery_item (delivery_id, item_id, outsource_material_id, quantity, quality_type, unit_price, company_id) VALUES ($auditedId, $moiId, $matId, 5, 'GOOD', 1, $CID)")
  Info "fixture: mo=$moId moi=$moiId auditedDelivery=$auditedId (wh=$whId mat=$matId)"
  $sameBody = @{ warehouseId = [long]$whId; items = @(@{ itemId = [long]$moiId; quantity = 5 }) }
  ExpectReject 'receive with identical lines' (Req 'POST' ("/outsource/material-order/$moId/receive") $admin $sameBody) '明细与本单完全一致'
  # positive control: a DIFFERENT quantity must NOT be treated as a duplicate (normal split receiving)
  $diffBody = @{ warehouseId = [long]$whId; items = @(@{ itemId = [long]$moiId; quantity = 4 }) }
  $rd2 = Req 'POST' ("/outsource/material-order/$moId/receive") $admin $diffBody
  if ((BMsg $rd2) -like '*明细与本单完全一致*') { Bad 'a different quantity was wrongly treated as a duplicate (over-blocking)' }
  else { Ok ('a different quantity is not treated as a duplicate (message: ' + (BMsg $rd2) + ')') }
  # clean up any draft the positive control just created (select by source_order_id: the code prefix
  # is generated server-side, so matching by prefix would be brittle)
  SqlExec "DELETE FROM outsource_delivery_item WHERE delivery_id IN (SELECT id FROM outsource_delivery WHERE source_order_id=$moId)"
  SqlExec "DELETE FROM outsource_delivery WHERE source_order_id=$moId"
} else { Skip 'missing supplier/warehouse/material for the F7-72 fixture' }

# ---------- F7-73 ----------
Write-Output ''
Write-Output '=== F7-73) weighted price excludes CANCELLED orders ==='
$mat73 = 0
$mr = Req 'POST' '/outsource/material' $admin @{ materialName = 'VERIFY-C-MAT2'; unit = 'PCS' }
if ((BCode $mr) -eq 200 -and $null -ne $mr.data) { $mat73 = [int]$mr.data }
$moA = 0; $moB = 0
if ($mat73 -gt 0 -and $supId) {
  # CANCELLED order priced at 999  +  live order priced at 1  -> expected weighted price = 1
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-C-MA', 'CANCELLED', $supId, 'OUTSOURCE', $CID)")
  $moA = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-C-MA' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moA, $mat73, 100, 0, 999, 99900, $CID)")
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id) VALUES ('VERIFY-C-MB', 'RECEIVING', $supId, 'OUTSOURCE', $CID)")
  $moB = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-C-MB' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moB, $mat73, 100, 0, 1, 100, $CID)")
  $rp = Req 'GET' ("/outsource/delivery/material-weighted-price?factoryId=$supId&materialId=$mat73") $admin $null
  $pv = [double]($rp.data)
  Info ("weighted price = " + $pv + "  (cancelled order @999, live order @1; expected 1)")
  if ((BCode $rp) -eq 200 -and [math]::Abs($pv - 1.0) -lt 0.01) { Ok 'CANCELLED order excluded from the weighted price (F7-73)' }
  else { Bad ('weighted price ' + $pv + ' (expected ~1; ~500 means cancelled orders still counted)') }
} else { Skip 'cannot build the F7-73 fixture' }

# ---------- F7-78 ----------
Write-Output ''
Write-Output '=== F7-78) return-order audit fails loudly when the factory has no outsource warehouse ==='
$roId = 0
$fakeFactory = 0
$invWh = SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND status=1 ORDER BY id LIMIT 1"
$masterProd = SqlOne "SELECT product_id FROM outsource_order_product WHERE product_id IS NOT NULL ORDER BY id LIMIT 1"
if ($invWh -and $masterProd -and $matId) {
  # A REAL supplier row is required (a non-existent factory id is rejected earlier with "加工厂不存在"),
  # and this brand-new supplier deliberately has NO outsource warehouse (all 13 existing ones do).
  SqlExec ("INSERT INTO supplier (code, name, status, company_id) VALUES ('VERIFY-C-SUP', 'VERIFY-C-SUP', 1, $CID)")
  $fakeFactory = [int](SqlOne "SELECT id FROM supplier WHERE code='VERIFY-C-SUP' ORDER BY id DESC LIMIT 1")
  Info "fixture: factory without outsource warehouse = supplier id $fakeFactory"
  SqlExec ("INSERT INTO outsource_return_order (code, return_type, status, factory_id, warehouse_id, return_date, closed_flag, company_id) VALUES ('VERIFY-C-RO', 'DEFECT', 'DRAFT', $fakeFactory, $invWh, CURDATE(), 0, $CID)")
  $roId = [int](SqlOne "SELECT id FROM outsource_return_order WHERE code='VERIFY-C-RO' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_return_order_product (return_order_id, product_id, product_name, quantity, company_id, quality_type) VALUES ($roId, $masterProd, 'VERIFY-C', 1, $CID, 'A')")
  SqlExec ("INSERT INTO outsource_return_order_item (return_order_id, outsource_material_id, quantity, unit_price, amount, company_id) VALUES ($roId, $matId, 1, 1, 1, $CID)")
  $ra = Req 'PUT' ("/outsource/return-order/$roId/audit") $admin $null
  ExpectReject 'audit with a factory that has no outsource warehouse' $ra '未配置委外仓库'
  $st = SqlOne "SELECT status FROM outsource_return_order WHERE id=$roId"
  if ($st -eq 'DRAFT') { Ok ('the return order stayed DRAFT (no partial posting, status=' + $st + ')') }
  else { Bad ('status changed to ' + $st + ' despite the rejection') }
} else { Skip 'cannot build the F7-78 fixture' }

# ---------- cleanup + self-check ----------
Write-Output ''
Write-Output '=== cleanup + self-check ==='
if ($roId -gt 0)  { SqlExec ("DELETE FROM outsource_return_order_item WHERE return_order_id=$roId") }
if ($roId -gt 0)  { SqlExec ("DELETE FROM outsource_return_order_product WHERE return_order_id=$roId") }
if ($roId -gt 0)  { SqlExec ("DELETE FROM outsource_return_order WHERE id=$roId") }
if ($auditedId -gt 0) { SqlExec ("DELETE FROM outsource_delivery_item WHERE delivery_id=$auditedId") }
if ($auditedId -gt 0) { SqlExec ("DELETE FROM outsource_delivery WHERE id=$auditedId") }
if ($moId -gt 0)  { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moId") }
if ($moId -gt 0)  { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moId") }
if ($moA -gt 0)   { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moA") }
if ($moA -gt 0)   { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moA") }
if ($moB -gt 0)   { SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$moB") }
if ($moB -gt 0)   { SqlExec ("DELETE FROM outsource_material_order WHERE id=$moB") }
SqlExec "DELETE FROM supplier_material WHERE material_id IN (SELECT id FROM outsource_material WHERE material_name IN ('VERIFY-C-MAT','VERIFY-C-MAT2'))"
SqlExec "DELETE FROM outsource_material WHERE material_name IN ('VERIFY-C-MAT','VERIFY-C-MAT2')"
if ($fakeFactory -gt 0) { SqlExec ("DELETE FROM supplier WHERE id=$fakeFactory") }

$c1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material')
$c2 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$c3 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$c4 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery')
$c5 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_delivery_item')
$c6 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_return_order')
Info ("after cleanup: material=$c1(was $cntMat) mo=$c2(was $cntMo) moi=$c3(was $cntMoi) delivery=$c4(was $cntDv) deliveryItem=$c5(was $cntDvi) retOrder=$c6(was $cntRo)")
if ($c1 -eq $cntMat -and $c2 -eq $cntMo -and $c3 -eq $cntMoi -and $c4 -eq $cntDv -and $c5 -eq $cntDvi -and $c6 -eq $cntRo) {
  Ok 'all 6 tables back to their pre-run baseline'
} else { Bad 'cleanup incomplete (see counts above)' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
