# verify-fix-f7-131to137.ps1
#
# Regression for batch 6c-3e (F7-131 .. F7-137, 2026-09-20):
#   F7-131  GET /warehouse/{id}  -> new single-warehouse endpoint. The old detail page first asked
#           /warehouse/by-factory/{warehouseId} (wrong semantics: that endpoint filters by factoryId) and
#           THREW THE RESULT AWAY, then fetched /warehouse/page?pageSize=100 and did a front-end find
#           => silently blank whenever there are more than 100 warehouses.
#   F7-132  /warehouse/stock/by-warehouse/{id} now also returns materialTypeId + materialTypeSortOrder.
#           The front-end used to rank rows by a hard-coded CHINESE type name (['玻璃','驱动IC']) and to
#           pick the "glass" type by typeName === '玻璃' => renaming a type silently broke both.
#   F7-135  dead file views/outsource/delivery.vue removed (no route and no import pointed at it).
#   F7-136  material-order items expose a usable materialTypeName (the page used to print the numeric id).
#   F7-137  OUTSOURCE-type material orders must use a factory-tagged supplier (parity with §23 / F7-61),
#           while PURCHASE-type orders must stay unrestricted (no over-restriction).
#
# Everything is self-built and cleaned up (stock/orders restored, no DDL).
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
    $msg = 'transport-error'
    try {
      $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      $txt = $sr.ReadToEnd()
      if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
    } catch { }
    return [pscustomobject]@{ code = $sc; msg = $msg }
  }
}
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }
function BMsg($r)  { if ($null -eq $r) { return '' }; if ($null -eq $r.msg) { return '' }; return [string]$r.msg }

Write-Output '=== 0) login + baseline ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

# NOTE: the table names are outsource_material_order / outsource_material_order_item (NOT material_order).
# Getting this wrong is SILENT under `2>$null` (the query errors, SqlOne returns '', [int]'' = 0), which turns
# the cleanup assertion into a 0==0 tautology AND leaves the test rows behind -- it happened on the first run
# of this very script (4 stray MWO-2026092000x rows had to be deleted by hand). Hence the explicit existence
# check below, so a wrong table name fails loudly instead of silently "passing".
$tbl = SqlOne "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='outsource_material_order'"
if ([int]$tbl -lt 1) { Bad 'fixture table outsource_material_order not found - check table names'; Write-Output 'RESULT FAIL count=1'; exit 1 }
$MO_BASE = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$MOI_BASE = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
$NON_FACTORY = 26      # 测试供货商A1 (type_code = product)
$FACTORY = 34          # 测试加工厂A1 (type_code = factory)
$MAT = 33              # 测试物料A1
$whCheck = SqlOne "SELECT COUNT(*) FROM supplier s WHERE s.id=$NON_FACTORY AND EXISTS(SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id=s.id AND r.type_code='product')"
$facCheck = SqlOne "SELECT COUNT(*) FROM supplier_type_ref WHERE supplier_id=$FACTORY AND type_code='factory'"
if ([int]$whCheck -lt 1 -or [int]$facCheck -lt 1) { Skip 'supplier type fixtures missing'; Write-Output 'RESULT SKIP'; exit 0 }
Info "baseline: material_order=$MO_BASE items=$MOI_BASE | nonFactory=$NON_FACTORY factory=$FACTORY"

# ---------- 1) F7-131: single-warehouse endpoint ----------
Write-Output ''
Write-Output '=== 1) F7-131: GET /warehouse/{id} returns the same shape as a page row ==='
foreach ($wid in @(74, 66, 71)) {
  $r = Req 'GET' "/warehouse/$wid" $admin $null
  if ((BCode $r) -ne 200) { Bad ("GET /warehouse/$wid -> code=" + (BCode $r)); continue }
  $d = $r.data
  $expName = SqlOne "SELECT warehouse_name FROM warehouse WHERE id=$wid"
  $expStatus = SqlOne "SELECT status FROM warehouse WHERE id=$wid"
  $expFid = SqlOne "SELECT IFNULL(factory_id,'') FROM warehouse WHERE id=$wid"
  $expFname = ''
  if ($expFid -ne '') { $expFname = SqlOne "SELECT IFNULL(name,'') FROM supplier WHERE id=$expFid" }
  if ([string]$d.warehouseName -ne $expName) { Bad ("wh $wid name='" + $d.warehouseName + "' expected '" + $expName + "'"); continue }
  if ([string]$d.status -ne $expStatus) { Bad ("wh $wid status='" + $d.status + "' expected '" + $expStatus + "'"); continue }
  if ([string]$d.factoryName -ne $expFname) { Bad ("wh $wid factoryName='" + $d.factoryName + "' expected '" + $expFname + "'"); continue }
  Ok ("GET /warehouse/$wid -> name='" + $expName + "' status=" + $expStatus + " factoryName='" + $expFname + "' (matches DB)")
}
$rMissing = Req 'GET' '/warehouse/999999' $admin $null
if ((BCode $rMissing) -eq 200) { Bad 'GET /warehouse/999999 returned 200 for a non-existent warehouse' }
else { Ok ('non-existent warehouse rejected: ' + (BMsg $rMissing)) }

# ---------- 2) F7-132: stock rows carry the type id + sort order ----------
Write-Output ''
Write-Output '=== 2) F7-132: by-warehouse rows carry materialTypeId + materialTypeSortOrder ==='
$checked = 0; $badSort = 0; $missingField = 0
foreach ($wid in @(66, 67, 74)) {
  $r = Req 'GET' "/warehouse/stock/by-warehouse/$wid" $admin $null
  if ((BCode $r) -ne 200) { continue }
  foreach ($row in @($r.data)) {
    if ($null -eq $row.materialId) { continue }
    $checked++
    if ($null -eq $row.PSObject.Properties['materialTypeSortOrder']) { $missingField++; continue }
    $exp = SqlOne ("SELECT IFNULL(t.sort_order,999) FROM outsource_material m JOIN material_type t ON t.id=m.material_type_id WHERE m.id=" + [long]$row.materialId)
    if ($exp -eq '') { continue }
    if ([string]$row.materialTypeSortOrder -ne $exp) {
      $badSort++
      Bad ("material " + $row.materialId + " materialTypeSortOrder=" + $row.materialTypeSortOrder + " expected " + $exp)
    }
  }
}
if ($checked -lt 1) { Skip 'no stock rows to check (F7-132)'; }
elseif ($missingField -gt 0) { Bad ("$missingField of $checked rows missing materialTypeSortOrder") }
elseif ($badSort -eq 0) { Ok ("$checked stock rows all carry a correct materialTypeSortOrder (type-id based, no Chinese name)") }

# ---------- 3) F7-135: dead page removed ----------
Write-Output ''
Write-Output '=== 3) F7-135: dead page views/outsource/delivery.vue removed ==='
$dead = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views\outsource\delivery.vue'
if (Test-Path $dead) { Bad 'views/outsource/delivery.vue still exists' }
else { Ok 'dead page views/outsource/delivery.vue is gone (no route, no import)' }

# ---------- 4) F7-136: material-order items expose a real type name ----------
Write-Output ''
Write-Output '=== 4) F7-136: material-order items expose materialTypeName (not the numeric id) ==='
$rMo = Req 'GET' '/outsource/material-order/page?pageNum=1&pageSize=5' $admin $null
if ((BCode $rMo) -ne 200) { Bad ('material-order page failed: ' + (BMsg $rMo)) }
else {
  $items = 0; $bad = 0; $nulls = 0
  foreach ($rec in @($rMo.data.records)) {
    foreach ($it in @($rec.items)) {
      $items++
      if ($null -eq $it.materialTypeName -or [string]$it.materialTypeName -eq '') { $nulls++; continue }
      if ([string]$it.materialTypeName -match '^\d+$') { $bad++; Bad ('item ' + $it.id + ' materialTypeName is numeric: ' + $it.materialTypeName) }
    }
  }
  if ($items -eq 0) { Skip 'no material-order items to check (F7-136)' }
  elseif ($bad -eq 0 -and $nulls -eq 0) { Ok ("$items item rows all expose a non-numeric materialTypeName") }
  elseif ($bad -eq 0) { Info ("$nulls of $items rows have no type set (null) - acceptable") ; Ok ("no numeric materialTypeName in $items rows") }
}

# ---------- 5) F7-137: OUTSOURCE needs a factory, PURCHASE stays open ----------
Write-Output ''
Write-Output '=== 5) F7-137: OUTSOURCE order requires a factory-tagged supplier ==='
$mkItems = @(@{ materialId = $MAT; materialTypeId = 19; unit = '个'; orderQuantity = 1; unitPrice = 1; remark = 'VERIFY-F7137' })

$rBad = Req 'POST' '/outsource/material-order' $admin (@{ orderType = 'OUTSOURCE'; supplierId = $NON_FACTORY; items = $mkItems })
if ((BCode $rBad) -eq 200) { Bad 'OUTSOURCE order ACCEPTED a non-factory supplier (F7-137 not effective)' }
elseif ((BMsg $rBad) -like '*加工厂*') { Ok ('rejected a non-factory supplier for OUTSOURCE: ' + (BMsg $rBad)) }
else { Bad ('OUTSOURCE rejected with an unexpected message: ' + (BMsg $rBad)) }

$rPurch = Req 'POST' '/outsource/material-order' $admin (@{ orderType = 'PURCHASE'; supplierId = $NON_FACTORY; items = $mkItems })
$purchId = 0
if ((BCode $rPurch) -ne 200) { Bad ('PURCHASE order was rejected - OVER-RESTRICTION! ' + (BMsg $rPurch)) }
else { $purchId = [long]$rPurch.data; Ok 'PURCHASE order with a product-tagged supplier still accepted (no over-restriction)' }

$rFac = Req 'POST' '/outsource/material-order' $admin (@{ orderType = 'OUTSOURCE'; supplierId = $FACTORY; items = $mkItems })
$facId = 0
if ((BCode $rFac) -ne 200) { Bad ('OUTSOURCE order with a real factory was rejected: ' + (BMsg $rFac)) }
else { $facId = [long]$rFac.data; Ok 'OUTSOURCE order with a factory-tagged supplier accepted' }

# edit path must enforce the same rule (swap the supplier of the just-created OUTSOURCE order to a non-factory)
if ($facId -gt 0) {
  $rUpd = Req 'PUT' ("/outsource/material-order/" + $facId) $admin (@{ orderType = 'OUTSOURCE'; supplierId = $NON_FACTORY; items = $mkItems })
  if ((BCode $rUpd) -eq 200) { Bad 'update() ACCEPTED switching an OUTSOURCE order to a non-factory supplier (F7-137 not applied to edit)' }
  else { Ok ('update() also rejects a non-factory supplier: ' + (BMsg $rUpd)) }
}

# ---------- cleanup ----------
Write-Output ''
Write-Output '=== cleanup ==='
foreach ($oid in @($purchId, $facId)) {
  if ($oid -gt 0) {
    SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id=$oid")
    SqlExec ("DELETE FROM outsource_material_order WHERE id=$oid")
  }
}
$moNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$moiNow = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
if ($moNow -eq $MO_BASE -and $moiNow -eq $MOI_BASE) { Ok ("cleanup: material_order=$moNow items=$moiNow (back to baseline)") }
else { Bad ("cleanup mismatch: material_order=$moNow/$MO_BASE items=$moiNow/$MOI_BASE") }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=" + $script:skip + ")") ; exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip)
exit 1
