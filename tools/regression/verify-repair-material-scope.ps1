# Range guard (2026-09-27, user rule): the "actual materials used" registered with a repair return may ONLY
# be picked from a defined pool:
#   * 加工侧 (finished-goods repair): the BOM snapshot of THIS document's product rows
#   * 物料侧 (material repair):        the child materials (outsource_material_component) of the sent material
# The quantity itself is still free ("can exceed the BOM" stays valid) - this guard only closes the RANGE.
#
# Negative control: a material outside the pool must be REJECTED and must not move any stock.
# Positive control: a pool member is accepted; then the return record is cancelled (so the file is repeatable).
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

# ---------- login ----------
$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-repair-material-scope (no token)'; exit 1 }

# =====================================================================
# A. 加工側: pool = BOM snapshot items of this document's product rows
# =====================================================================
Step 'A fixture: an audited REPAIR return with a BOM-backed product row and room left'
$fx = @(((SqlRaw "SELECT o.id, p.product_id, p.bom_snapshot_id, o.warehouse_id FROM outsource_return_order o JOIN outsource_return_order_product p ON p.return_order_id = o.id WHERE o.return_type='REPAIR' AND o.status='AUDITED' AND IFNULL(o.closed_flag,0)=0 AND p.bom_snapshot_id IS NOT NULL AND o.warehouse_id IS NOT NULL AND (SELECT COUNT(*) FROM bom_snapshot_item bi WHERE bi.snapshot_id = p.bom_snapshot_id) > 0 AND (SELECT IFNULL(SUM(p2.quantity),0) FROM outsource_return_order_product p2 WHERE p2.return_order_id=o.id AND p2.product_id=p.product_id) > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_return_order_repair r WHERE r.return_order_id=o.id AND r.product_id=p.product_id) ORDER BY o.id DESC LIMIT 1") -split "`n")[1] -split "`t" | ForEach-Object { "$_".Trim() })
$roId = [int]$fx[0]; $roProd = [int]$fx[1]; $roSnap = [int]$fx[2]; $roWh = [int]$fx[3]
Write-Host ("FIXTURE-A: doc=$roId product=$roProd snapshot=$roSnap warehouse=$roWh")
if ($roId -le 0) { Info 'no 加工侧 fixture with a BOM-backed product row that still has room -> skipped (coverage gap)' } else {
  $roPool = SqlList "SELECT DISTINCT outsource_material_id FROM bom_snapshot_item WHERE snapshot_id=$roSnap AND outsource_material_id IS NOT NULL"
  $roGood = if ($roPool.Count -gt 0) { [int]$roPool[0] } else { 0 }
  $roCsv = ($roPool | ForEach-Object { "$_" }) -join ','
  $roBad = [int](SqlOne "SELECT id FROM outsource_material WHERE id NOT IN ($roCsv) ORDER BY id LIMIT 1")
  Write-Host ("POOL-A: n=" + $roPool.Count + " good=" + $roGood + " bad(outside)=" + $roBad)
  Ok ($roPool.Count -gt 0) 'the document BOM contributes at least one candidate material'
  Ok ($roBad -gt 0) 'found a material that is NOT in the BOM (negative-control input)'

  $baseRec = [int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE return_order_id=$roId"))
  $baseItem = [int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair_item i JOIN outsource_return_order_repair r ON r.id = i.repair_record_id WHERE r.return_order_id=$roId"))
  $badStockBefore = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$roBad")

  Step 'A-negative: a material outside the BOM pool must be rejected'
  $body = @{ warehouseId = $roWh; repairDate = '2026-09-27'; items = @(@{ productId = $roProd; qualityType = 'A'; quantity = 1 }); materials = @(@{ materialId = $roBad; quantity = 1 }) } | ConvertTo-Json -Depth 6
  $ra = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/return-order/$roId/repair-return" -Method Post -Headers $h -ContentType 'application/json' -Body $body
  Write-Host ('A-NEG: code=' + $ra.code + ' msg=' + $ra.msg)
  Ok ($ra.code -ne 200) 'a material outside the BOM is rejected'
  Ok ("$($ra.msg)" -match 'BOM') 'the rejection names the BOM rule'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE return_order_id=$roId"))) -eq $baseRec) 'no repair record was created'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair_item i JOIN outsource_return_order_repair r ON r.id = i.repair_record_id WHERE r.return_order_id=$roId"))) -eq $baseItem) 'no material row was created'
  Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$roBad")) -eq $badStockBefore) 'the rejected material did not move any stock'

  Step 'A-positive: a BOM member is accepted, then the return record is cancelled'
  $body2 = @{ warehouseId = $roWh; repairDate = '2026-09-27'; items = @(@{ productId = $roProd; qualityType = 'A'; quantity = 1 }); materials = @(@{ materialId = $roGood; quantity = 1 }) } | ConvertTo-Json -Depth 6
  $rp = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/return-order/$roId/repair-return" -Method Post -Headers $h -ContentType 'application/json' -Body $body2
  Write-Host ('A-POS: code=' + $rp.code + ' msg=' + $rp.msg)
  Ok ($rp.code -eq 200) 'a BOM member is accepted'
  $recId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_return_order_repair WHERE return_order_id=$roId")
  Ok ($recId -gt 0) ('a repair record was created (id=' + $recId + ')')
  $matRows = [int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair_item WHERE repair_record_id=$recId AND item_type='MATERIAL' AND material_id=$roGood"))
  Ok ($matRows -ge 1) 'the material usage line was written'
  $rd = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/return-order/repair-return/$recId" -Method Delete -Headers $h
  Write-Host ('A-CANCEL: code=' + $rd.code + ' msg=' + $rd.msg)
  Ok ($rd.code -eq 200) 'the return record can be cancelled again'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_return_order_repair WHERE id=$recId"))) -eq 0) 'the record is gone (repeatable)'
}

# =====================================================================
# B. 物料側: pool = child materials of the sent material
# =====================================================================
Step 'B fixture: an audited REPAIR material return whose sent material has children'
$fx2 = @(((SqlRaw "SELECT h.id, i.outsource_material_id, h.from_warehouse_id FROM outsource_material_return h JOIN outsource_material_return_item i ON i.return_order_id = h.id WHERE h.return_type='REPAIR' AND h.status='AUDITED' AND IFNULL(h.closed_flag,0)=0 AND h.from_warehouse_id IS NOT NULL AND EXISTS (SELECT 1 FROM outsource_material_component c WHERE c.parent_outsource_material_id = i.outsource_material_id) AND (SELECT IFNULL(SUM(i2.quantity),0) FROM outsource_material_return_item i2 WHERE i2.return_order_id=h.id AND i2.outsource_material_id=i.outsource_material_id) > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id=h.id AND r.material_id=i.outsource_material_id) ORDER BY h.id DESC LIMIT 1") -split "`n")[1] -split "`t" | ForEach-Object { "$_".Trim() })
$mrId = [int]$fx2[0]; $mrSent = [int]$fx2[1]; $mrWh = [int]$fx2[2]
Write-Host ("FIXTURE-B: doc=$mrId sentMaterial=$mrSent warehouse=$mrWh")
if ($mrId -le 0) { Info 'no 物料侧 fixture (sent material with children + room) -> skipped (coverage gap)' } else {
  $mrKids = SqlList "SELECT DISTINCT child_outsource_material_id FROM outsource_material_component WHERE parent_outsource_material_id=$mrSent AND child_outsource_material_id IS NOT NULL"
  $mrGood = if ($mrKids.Count -gt 0) { [int]$mrKids[0] } else { 0 }
  $mrCsv = ($mrKids | ForEach-Object { "$_" }) -join ','
  $mrBad = [int](SqlOne "SELECT id FROM outsource_material WHERE id NOT IN ($mrCsv) ORDER BY id LIMIT 1")
  Write-Host ("POOL-B: n=" + $mrKids.Count + " good=" + $mrGood + " bad(outside)=" + $mrBad)
  Ok ($mrKids.Count -gt 0) 'the sent material contributes at least one child-material candidate'
  Ok ($mrBad -gt 0) 'found a material that is NOT a child (negative-control input)'

  $baseRecB = [int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$mrId"))
  $badStockBeforeB = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$mrBad")

  Step 'B-negative: a material that is not a child of the sent material must be rejected'
  $bodyB = @{ warehouseId = $mrWh; repairDate = '2026-09-27'; items = @(@{ materialId = $mrSent; quantity = 1 }); materials = @(@{ materialId = $mrBad; quantity = 1 }) } | ConvertTo-Json -Depth 6
  $rb = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/material-return/$mrId/repair-return" -Method Post -Headers $h -ContentType 'application/json' -Body $bodyB
  Write-Host ('B-NEG: code=' + $rb.code + ' msg=' + $rb.msg)
  Ok ($rb.code -ne 200) 'a non-child material is rejected'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE return_order_id=$mrId"))) -eq $baseRecB) 'no repair record was created'
  Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE material_id=$mrBad")) -eq $badStockBeforeB) 'the rejected material did not move any stock'

  Step 'B-positive: a child material is accepted, then the return record is cancelled'
  $bodyB2 = @{ warehouseId = $mrWh; repairDate = '2026-09-27'; items = @(@{ materialId = $mrSent; quantity = 1 }); materials = @(@{ materialId = $mrGood; quantity = 1 }) } | ConvertTo-Json -Depth 6
  $rbp = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/material-return/$mrId/repair-return" -Method Post -Headers $h -ContentType 'application/json' -Body $bodyB2
  Write-Host ('B-POS: code=' + $rbp.code + ' msg=' + $rbp.msg)
  Ok ($rbp.code -eq 200) 'a child material is accepted'
  $recIdB = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_material_return_repair WHERE return_order_id=$mrId")
  Ok ($recIdB -gt 0) ('a repair record was created (id=' + $recIdB + ')')
  $matRowsB = [int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair_material WHERE repair_record_id=$recIdB AND material_id=$mrGood"))
  Ok ($matRowsB -ge 1) 'the child-material usage line was written'
  $rdB = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/material-return/repair-return/$recIdB" -Method Delete -Headers $h
  Write-Host ('B-CANCEL: code=' + $rdB.code + ' msg=' + $rdB.msg)
  Ok ($rdB.code -eq 200) 'the return record can be cancelled again'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE id=$recIdB"))) -eq 0) 'the record is gone (repeatable)'
}

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-repair-material-scope (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
