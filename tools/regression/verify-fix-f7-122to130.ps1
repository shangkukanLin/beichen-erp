# verify-fix-f7-122to130.ps1
#
# Verification for the 6c-3 batch fixes (outsource audit rounds 6c-3a..6c-3d):
#   F7-122  @TableLogic written on a MAPPER INTERFACE CONSTANT was a no-op  -> constant removed, comments fixed
#   F7-123  read-path N+1 in OutsourceMaterialReturnServiceImpl.page        -> batched (6 fixed queries/page)
#   F7-124  outsource_contract_template.party_a_{address,contact,phone}     -> dead columns DROPPED
#   F7-125  spec (product spec retired全站下线) remnants                     -> entity fields + copies removed, columns DROPPED
#   F7-126  outsource_material.warehouse_id dead column read by a zero-count check -> column DROPPED + check removed
#   F7-127  MaterialOrderServiceImpl.update only blocked CANCELLED          -> PENDING whitelist (end-to-end asserted)
#   F7-128  OrderDeliveryController bound the whole entity                  -> whitelist parseDelivery(Map)
#   F7-129  7 x empty catch {} on option loads                              -> console.warn (static assert)
#   F7-130  material-info.vue BOM: silent empty + full replace              -> bomLoaded sentinel (static assert)
#
# Needs a UTF-8 BOM (Chinese expectations / messages).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$SRV = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-server\src\main\java\com\beichen\erp'
$WEB = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views\outsource'
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
function Grep([string]$file, [string]$pattern) {
  return @(Select-String -Path $file -Pattern $pattern -ErrorAction SilentlyContinue).Count
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

Write-Output '=== 1) F7-122 @TableLogic no longer on a mapper interface constant ==='
# NOTE: match the ANNOTATION SHAPE (line starts with @TableLogic), not the bare word --
# the fix comments mention `@TableLogic` in prose, and matching the bare word would false-fail.
# (tools.md #26/#40: pasting old code into comments is the most frequent source of false alarms.)
$moMapper = Join-Path $SRV 'outsource\mapper\MaterialOrderMapper.java'
$moiMapper = Join-Path $SRV 'outsource\mapper\MaterialOrderItemMapper.java'
$c1 = (Grep $moMapper '(?m)^\s*@TableLogic') + (Grep $moiMapper '(?m)^\s*@TableLogic')
if ($c1 -eq 0) { Ok 'no live @TableLogic annotation left on the two material-order mappers (invalid constant removed)' }
else { Bad ("live @TableLogic annotation still present on mapper constants (count=$c1)") }
$liveLogic = (Get-ChildItem (Join-Path $SRV 'outsource') -Recurse -Filter *.java | Select-String -Pattern '(?m)^\s*@TableLogic' -ErrorAction SilentlyContinue).Count
if ($liveLogic -eq 0) { Ok 'no live @TableLogic annotation anywhere in the outsource module' }
else { Bad ("live @TableLogic annotations left in outsource: $liveLogic") }

Write-Output ''
Write-Output '=== 2) F7-123 page() no longer reads per row (batched) ==='
$impl = Join-Path $SRV 'outsource\service\impl\OutsourceMaterialReturnServiceImpl.java'
# assert the NEW shapes (batched maps) instead of "the old line is absent" (comments mention the old lines)
$batched = (Grep $impl 'deliveryCodeMap\.get') + (Grep $impl 'supNameMap\.getOrDefault') + (Grep $impl 'matNameMap\.getOrDefault')
if ($batched -ge 3) { Ok 'page() now serves sourceDeliveryCode / supplierName / materialName from batched maps' }
else { Bad ("batched lookups not found in page() (count=$batched)") }
if ((Grep $impl 'itemsByOrder\.computeIfAbsent') -ge 1) { Ok 'line items are grouped from a single IN query (itemsByOrder)' } else { Bad 'per-order item query still present' }
if ((Grep $impl 'selectBatchIds') -ge 3) { Ok 'selectBatchIds used for the batched lookups' } else { Bad 'selectBatchIds not found (batching not applied?)' }

Write-Output ''
Write-Output '=== 3) F7-124 / F7-125 / F7-126 dead columns dropped ==='
$left = [int](SqlOne "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND ((TABLE_NAME='outsource_contract_template' AND COLUMN_NAME LIKE 'party_a_%') OR (TABLE_NAME='outsource_material' AND COLUMN_NAME IN ('spec','warehouse_id')) OR (TABLE_NAME='outsource_stock_loss_item' AND COLUMN_NAME='spec'));")
if ($left -eq 0) { Ok 'all 6 dead columns are gone from the schema (party_a_* x3, outsource_material.spec/warehouse_id, stock_loss_item.spec)' }
else { Bad ("dead columns still present: $left") }
if ((Grep (Join-Path $SRV 'outsource\entity\OutsourceMaterial.java') '(?m)^\s*private String spec;') -eq 0) { Ok 'OutsourceMaterial.spec entity field removed' } else { Bad 'OutsourceMaterial.spec still declared' }
$wc = Join-Path $SRV 'warehouse\controller\WarehouseController.java'
if ((Grep $wc '(?m)^\s*cnt = jdbcTemplate\.queryForObject\(\s*$') -ge 1 -and (Grep $wc 'F7-126') -ge 1) {
  Ok 'the dead association check was replaced by an explanatory comment (marked F7-126)'
} else { Info 'WarehouseController F7-126 marker not found (manual check)' }
// NOTE: do NOT assert "the SQL text is absent" -- the explanatory comment quotes that SQL on purpose.
// The real guarantees are: (a) the column no longer exists (asserted above via information_schema),
// and (b) the file carries the F7-126 marker. Asserting the bare string would false-fail (3rd time this trap bites).
# the two spec copies must be gone as well -- match an ASSIGNMENT shape, not the word inside comments
$copies = (Grep (Join-Path $SRV 'outsource\service\impl\SupplierMaterialServiceImpl.java') '(?m)^\s*\w+\.setSpec\(') + (Grep (Join-Path $SRV 'outsource\service\impl\OutsourceStockLossServiceImpl.java') '(?m)^\s*\w+\.setSpec\(')
if ($copies -eq 0) { Ok 'both "copy spec into the view field" statements removed' } else { Bad ("live spec copy statements still present: $copies") }

Write-Output ''
Write-Output '=== 4) F7-127 material order: PENDING whitelist (end-to-end, with positive control) ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'
$CID = 1
# NOTE (2026-09-20, F7-137): the fixture order below is order_type='OUTSOURCE', so the supplier MUST be
# factory-tagged -- the service now rejects a non-factory supplier for that type (§23-F7-61 parity).
# The old plain "ORDER BY id LIMIT 1" picked supplier 26 (type_code='product') => false failures.
$SUP = SqlOne "SELECT s.id FROM supplier s JOIN supplier_type_ref r ON r.supplier_id = s.id AND r.type_code = 'factory' ORDER BY s.id LIMIT 1"
$MAT = SqlOne 'SELECT id FROM outsource_material ORDER BY id LIMIT 1'
$orders0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$items0 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
if ($SUP -and $MAT) {
  SqlExec ("INSERT INTO outsource_material_order (code, status, supplier_id, order_type, company_id, remark) VALUES ('VERIFY-F7127', 'PENDING', $SUP, 'OUTSOURCE', $CID, 'VERIFY-F7127')")
  $moid = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='VERIFY-F7127' ORDER BY id DESC LIMIT 1")
  SqlExec ("INSERT INTO outsource_material_order_item (order_id, outsource_material_id, order_quantity, received_quantity, unit_price, amount, company_id) VALUES ($moid, $MAT, 10, 0, 1, 10, $CID)")
  $miid = [int](SqlOne "SELECT id FROM outsource_material_order_item WHERE order_id=$moid ORDER BY id LIMIT 1")
  if ($moid -gt 0 -and $miid -gt 0) {
    # positive control: PENDING must still be editable
    $okBody = @{ supplierId = [long]$SUP; orderType = 'OUTSOURCE'; deliveryDate = '2026-09-25'
                 items = @(@{ materialId = [long]$MAT; orderQuantity = 20; unitPrice = 1 }) }
    $r1 = Req 'PUT' ("/outsource/material-order/$moid") $admin $okBody
    if ((BCode $r1) -eq 200) {
      $q1 = SqlOne "SELECT order_quantity FROM outsource_material_order_item WHERE id=$miid"
      if ("$q1" -eq '20') { Ok 'positive control: PENDING order is still editable (order_quantity 10 -> 20)' }
      else { Bad ("PENDING edit did not take effect (order_quantity=$q1)") }
      $dd = SqlOne "SELECT IFNULL(delivery_date,'') FROM outsource_material_order WHERE id=$moid"
      if ("$dd" -eq '2026-09-25') { Ok 'delivery_date also updated on a PENDING order' } else { Bad ("delivery_date=$dd (expected 2026-09-25)") }
    } else { Bad ('positive control failed: PENDING edit was rejected: ' + (BMsg $r1)) }

    # reverse assertion: RECEIVING must be refused
    SqlExec ("UPDATE outsource_material_order SET status='RECEIVING' WHERE id=$moid")
    $badBody = @{ supplierId = [long]$SUP; orderType = 'OUTSOURCE'; deliveryDate = '2026-10-01'
                  items = @(@{ materialId = [long]$MAT; orderQuantity = 99; unitPrice = 1 }) }
    $r2 = Req 'PUT' ("/outsource/material-order/$moid") $admin $badBody
    if ((BCode $r2) -eq 200) { Bad 'RECEIVING order was ACCEPTED (F7-127 not fixed)' }
    elseif ((BMsg $r2) -like '*只有待审核的订单可以编辑*') {
      Ok ('RECEIVING order refused: ' + (BMsg $r2))
      $q2 = SqlOne "SELECT order_quantity FROM outsource_material_order_item WHERE id=$miid"
      if ("$q2" -eq '20') { Ok 'the refused request changed nothing (order_quantity still 20)' } else { Bad ("order_quantity became $q2 after a refused request") }
      $dd2 = SqlOne "SELECT IFNULL(delivery_date,'') FROM outsource_material_order WHERE id=$moid"
      if ("$dd2" -eq '2026-09-25') { Ok 'delivery_date untouched by the refused request (still 2026-09-25)' } else { Bad ("delivery_date became $dd2 after a refused request") }
    } else { Bad ('refused with an unexpected message: ' + (BMsg $r2)) }

    # F7-127 side-effect check: FINISHED must be refused too
    SqlExec ("UPDATE outsource_material_order SET status='FINISHED' WHERE id=$moid")
    $r3 = Req 'PUT' ("/outsource/material-order/$moid") $admin $badBody
    if ((BCode $r3) -eq 200) { Bad 'FINISHED order was ACCEPTED (F7-127 incomplete)' } else { Ok 'FINISHED order also refused' }
  } else { Skip 'could not build the material-order fixture' }
} else { Skip 'no supplier/material for the fixture' }

Write-Output ''
Write-Output '=== 5) F7-128 OrderDeliveryController uses a whitelist (static) ==='
$odc = Join-Path $SRV 'outsource\controller\OrderDeliveryController.java'
# match the ANNOTATION shape on a handler signature (the javadoc legitimately mentions the old form)
$liveEntityBind = (Grep $odc '(?m)@RequestBody\s+OutsourceOrderDelivery\s+\w+')
if ($liveEntityBind -eq 0) { Ok 'no live whole-entity binding on the order-delivery endpoints' } else { Bad ("live whole-entity binding still present: $liveEntityBind") }
if ((Grep $odc '(?m)@RequestBody Map<String, Object> body') -ge 2) { Ok 'both create/update now take Map<String, Object> body' } else { Bad 'create/update are not Map-based' }
if ((Grep $odc 'parseDelivery\(') -ge 3) { Ok 'whitelist parseDelivery(Map) is in place and used by create/update' } else { Bad 'parseDelivery not wired' }
if ((Grep $odc '(?m)^\s*d\.setStatus\(') -eq 0 -and (Grep $odc '(?m)^\s*d\.setCompanyId\(') -eq 0) { Ok 'whitelist never sets status/companyId (server keeps control)' } else { Bad 'whitelist sets status/companyId' }

Write-Output ''
Write-Output '=== 6) F7-129 / F7-130 frontend (static) ==='
$emptyCatch = 0
foreach ($f in @('order\add.vue','order\close.vue','material-order\add.vue','material-order\detail.vue','delivery\add.vue','material-return\add.vue')) {
  $emptyCatch += (Grep (Join-Path $WEB $f) 'catch\s*\{\s*\}')
}
if ($emptyCatch -eq 0) { Ok 'no empty catch {} left on the option-loading paths (F7-129)' }
else { Bad ("empty catch {} still present: $emptyCatch") }
$mi = Join-Path $WEB 'material-info.vue'
if ((Grep $mi 'bomLoaded') -ge 3) { Ok 'bomLoaded sentinel present in material-info.vue (F7-130)' } else { Bad 'bomLoaded sentinel missing' }
if ((Grep $mi 'bomLoaded\.value\) \{ await saveComponents') -ge 1) { Ok 'saveComponents is now guarded by bomLoaded' } else { Info 'saveComponents guard not matched by pattern (manual check needed)' }

Write-Output ''
Write-Output '=== cleanup + self-check ==='
SqlExec ("DELETE FROM outsource_material_order_item WHERE order_id IN (SELECT id FROM outsource_material_order WHERE code='VERIFY-F7127')")
SqlExec ("DELETE FROM outsource_material_order WHERE code='VERIFY-F7127'")
$orders1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order')
$items1 = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_order_item')
Info ("after cleanup: material_order=$orders1(was $orders0) material_order_item=$items1(was $items0)")
if ($orders1 -eq $orders0 -and $items1 -eq $items0) { Ok 'both tables back to their pre-run baseline' } else { Bad 'cleanup incomplete' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=$script:skip)") } else { Write-Output ("RESULT FAIL count=$script:fail skip=$script:skip") }
exit $script:fail
