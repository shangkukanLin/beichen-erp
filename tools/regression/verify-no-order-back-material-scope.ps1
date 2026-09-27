# Guard (2026-09-27, user rules):
#   A) Creating a "no-order processing return" must persist a BOM snapshot on the record:
#      - not passed  -> auto-resolve = the snapshot that product last used on an ORDER at that factory
#                       (= the first item of /product-snapshot-options)
#      - passed      -> use it as-is (manual version switch)
#   B) A "processing return back" (return-back) may only register material usage from the SOURCE
#      record's BOM snapshot: an out-of-pool material must be rejected (nothing persisted), an in-pool
#      one must pass. An empty pool (no snapshot / no BOM) allows "return only, no material lines".
# Self-creating + self-cleaning (create -> audit -> create back -> delete back -> un-audit -> delete), repeatable.
# PURE ASCII (Chinese only ever appears in DB data, never in this file).
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlList([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  return @(($v -split "`n" | Select-Object -Skip 1) | ForEach-Object { (($_ -split "`t")[0]).Trim() } | Where-Object { $_ -ne '' })
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-no-order-back-material-scope (no token)'; exit 1 }

Step 'fixture: finished stock (A>=2) + a factory that already ordered that product with a BOM snapshot'
$fx = @(((SqlRaw "SELECT ws.warehouse_id, ws.product_id FROM warehouse_stock ws JOIN warehouse w ON w.id=ws.warehouse_id WHERE ws.quality_type='A' AND ws.quantity >= 2 AND w.warehouse_category='INVENTORY' AND w.warehouse_type='FINISHED' AND EXISTS (SELECT 1 FROM outsource_order_product op JOIN outsource_order o ON o.id=op.order_id WHERE op.product_id=ws.product_id AND op.bom_snapshot_id IS NOT NULL AND o.factory_id IS NOT NULL) ORDER BY ws.quantity DESC LIMIT 1") -split "`n")[1] -split "`t" | ForEach-Object { "$_".Trim() })
$whId = [int]$fx[0]; $masterId = [int]$fx[1]
Write-Host ("FIXTURE: warehouse=$whId product=$masterId")
if ($whId -le 0 -or $masterId -le 0) { Info 'no finished-goods A stock fixture -> skipped'; Write-Host 'RESULT PASS verify-no-order-back-material-scope (skipped)'; exit 0 }

$fac = [int](SqlOne "SELECT o.factory_id FROM outsource_order o JOIN outsource_order_product op ON op.order_id=o.id AND op.product_id=$masterId AND op.bom_snapshot_id IS NOT NULL WHERE o.factory_id IS NOT NULL ORDER BY o.id DESC LIMIT 1")
$facHasWh = [int](SqlOne "SELECT COUNT(*) FROM warehouse WHERE factory_id=$fac AND warehouse_category='OUTSOURCE'")
Write-Host ("FACTORY: id=$fac outsourceWarehouseCount=$facHasWh")
Ok ($fac -gt 0) 'found a factory that ordered this product with a BOM snapshot'
Ok ($facHasWh -gt 0) 'that factory has an OUTSOURCE warehouse'
if ($fac -le 0 -or $facHasWh -le 0) { Info 'fixture incomplete -> skipped'; Write-Host 'RESULT PASS verify-no-order-back-material-scope (skipped)'; exit 0 }

$opts = Invoke-RestMethod -Uri ("http://localhost:8080/api/outsource/order-delivery/product-snapshot-options?factoryId=$fac&productMasterId=$masterId") -Headers $h
$optCount = @($opts.data).Count
$expectSnap = if ($optCount -gt 0) { [int]$opts.data[0].snapshotId } else { 0 }
Write-Host ("OPTIONS: n=$optCount first=$expectSnap")
Ok ($expectSnap -gt 0) 'the options endpoint returns at least one snapshot (first = most recently used)'

# =====================================================================
# A. create without bomSnapshotId -> auto resolve
# =====================================================================
Step 'A: create the no-order return WITHOUT a snapshot -> auto-resolved to the most recently order-used one'
$body = @{ factoryId = $fac; warehouseId = $whId; productMasterId = $masterId; qualityType = 'A'; quantity = 1; remark = 'probe:auto snapshot (temp)' } | ConvertTo-Json -Depth 5
$r = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/order-delivery/return-defect-no-order' -Method Post -Headers $h -ContentType 'application/json' -Body $body
Write-Host ('create(auto): code=' + $r.code + ' msg=' + $r.msg)
Ok ($r.code -eq 200) 'draft created without passing a snapshot'
$docId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
$gotSnap = [int](SqlOne "SELECT IFNULL(bom_snapshot_id,0) FROM outsource_order_delivery WHERE id=$docId")
Write-Host ("doc=$docId bomSnapshotId=$gotSnap expected=$expectSnap")
Ok ($gotSnap -eq $expectSnap) 'the snapshot was auto-resolved to the most recently order-used one'

Step 'A2: create WITH an explicit snapshot -> stored as-is'
$otherSnap = [int](SqlOne "SELECT id FROM bom_snapshot WHERE product_master_id=$masterId ORDER BY bom_version DESC LIMIT 1")
if ($otherSnap -le 0) { $otherSnap = $expectSnap }
$body2 = @{ factoryId = $fac; warehouseId = $whId; productMasterId = $masterId; qualityType = 'A'; quantity = 1; remark = 'probe:explicit snapshot (temp)'; bomSnapshotId = $otherSnap } | ConvertTo-Json -Depth 5
$r2 = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/order-delivery/return-defect-no-order' -Method Post -Headers $h -ContentType 'application/json' -Body $body2
$docId2 = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_order_delivery")
$gotSnap2 = [int](SqlOne "SELECT IFNULL(bom_snapshot_id,0) FROM outsource_order_delivery WHERE id=$docId2")
Write-Host ("create(explicit): code=" + $r2.code + " doc=$docId2 snapshot=$gotSnap2 asked=$otherSnap")
Ok ($r2.code -eq 200) 'draft created with an explicit snapshot'
Ok ($gotSnap2 -eq $otherSnap) 'the explicit snapshot is stored as-is'
Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/order-delivery/$docId2" -Method Delete -Headers $h | Out-Null

# =====================================================================
# B. return-back material scope
# =====================================================================
Step 'B: audit the source (finished goods into the factory outsource warehouse), then try pool-outside/in-pool'
$au = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/order-delivery/$docId/audit" -Method Put -Headers $h
Write-Host ('audit source: code=' + $au.code + ' msg=' + $au.msg)
Ok ($au.code -eq 200) 'the source no-order return is audited'

$pool = SqlList "SELECT DISTINCT outsource_material_id FROM bom_snapshot_item WHERE snapshot_id=$expectSnap AND outsource_material_id IS NOT NULL"
$poolCsv = ($pool | ForEach-Object { "$_" }) -join ','
$good = if ($pool.Count -gt 0) { [int]$pool[0] } else { 0 }
$bad = [int](SqlOne "SELECT id FROM outsource_material WHERE id NOT IN ($poolCsv) ORDER BY id LIMIT 1")
Write-Host ("POOL: n=" + $pool.Count + " inPool=" + $good + " outOfPool=" + $bad)
Ok ($pool.Count -gt 0) 'the snapshot contributes at least one candidate material'
Ok ($bad -gt 0) 'found a material outside the snapshot (negative-control input)'

$inWh = [int](SqlOne "SELECT id FROM warehouse WHERE warehouse_category='INVENTORY' AND warehouse_type='FINISHED' ORDER BY id LIMIT 1")

Step 'B-negative: an out-of-snapshot material must be rejected and must not be persisted'
$bb = @{ factoryId = $fac; productId = $masterId; quantity = 1; defectQualityType = 'A'; returnQualityType = 'A'; inWarehouseId = $inWh; sourceDeliveryId = $docId; returnDate = '2026-09-27'; remark = 'probe:out-of-pool (temp)'; items = @(@{ materialId = $bad; quantity = 1 }) } | ConvertTo-Json -Depth 6
$rb = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/return-back' -Method Post -Headers $h -ContentType 'application/json' -Body $bb
Write-Host ('B-NEG: code=' + $rb.code + ' msg=' + $rb.msg)
Ok ($rb.code -ne 200) 'a material outside the source snapshot is rejected'
Ok ("$($rb.msg)" -match 'BOM') 'the rejection names the BOM-snapshot rule'
Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back WHERE source_delivery_id=$docId"))) -eq 0) 'no return-back row was created'

Step 'B-positive: an in-snapshot material passes'
$bp = @{ factoryId = $fac; productId = $masterId; quantity = 1; defectQualityType = 'A'; returnQualityType = 'A'; inWarehouseId = $inWh; sourceDeliveryId = $docId; returnDate = '2026-09-27'; remark = 'probe:in-pool (temp)'; items = @(@{ materialId = $good; quantity = 1 }) } | ConvertTo-Json -Depth 6
$rp = Invoke-RestMethod -Uri 'http://localhost:8080/api/outsource/return-back' -Method Post -Headers $h -ContentType 'application/json' -Body $bp
Write-Host ('B-POS: code=' + $rp.code + ' msg=' + $rp.msg)
Ok ($rp.code -eq 200) 'a material inside the snapshot is accepted'
$backId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_back")
Ok ($backId -gt 0) ('return-back draft created (id=' + $backId + ')')
Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_back_item WHERE return_back_id=$backId AND material_id=$good"))) -ge 1) 'the material line was stored'

# =====================================================================
# cleanup
# =====================================================================
Step 'cleanup (keeps this probe repeatable)'
$d1 = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/return-back/$backId" -Method Delete -Headers $h
Ok ($d1.code -eq 200) 'return-back draft deleted'
$ua = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/order-delivery/$docId/un-audit" -Method Put -Headers $h
Write-Host ('un-audit source: code=' + $ua.code + ' msg=' + $ua.msg)
Ok ($ua.code -eq 200) 'source un-audited (factory on-site reversed)'
$d2 = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/order-delivery/$docId" -Method Delete -Headers $h
Ok ($d2.code -eq 200) 'source draft deleted'
Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_order_delivery WHERE id IN ($docId,$docId2)"))) -eq 0) 'no leftover probe records'

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-no-order-back-material-scope (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
