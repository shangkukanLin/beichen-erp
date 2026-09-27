# Symmetry guard for the material-side "on-site for repair" row (warehouse_stock form=MATERIAL_REPAIR).
#
# CASE A (the original bug, 2026-09-27): repairReturn() deducts the on-site row UNCONDITIONALLY
#   (allocateOnSiteRepair), so cancelRepairReturn() must restore it UNCONDITIONALLY too. The restore used to
#   sit inside `if (!mats.isEmpty())` => a return WITHOUT material-usage lines leaked the on-site quantity and
#   the later un-audit failed with "on-site material insufficient" (found by ui-e2e-15 S8).
#   Fixture is built from scratch: create a REPAIR doc, audit it (on-site +qty), register a return without
#   material lines, cancel it (on-site must come back), un-audit (on-site must return to base).
#
# CASE B (the second-order edge found while fixing A): for a LEGACY document (audited before the 2026-09-25
#   material-form change) the on-site row never got the sent quantity, so the registration SKIPS the deduction
#   (`allocateOnSiteRepair` returns false, the record is flagged onsite_leg=0). The cancellation must therefore
#   ALSO skip the restore - otherwise it invents +qty out of nothing (reverse leak).
#
# Fixtures are self-built / self-cleaned, so the file is repeatable. PURE ASCII.
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
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @(($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
$API = 'http://localhost:8080/api/outsource/material-return'

$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-repair-cancel-onsite (no token)'; exit 1 }

function OnSite([int]$whId, [int]$matId) {
  return D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$whId AND material_id=$matId AND stock_form='MATERIAL_REPAIR'")
}

# =====================================================================
# CASE A: form-era document -> registration deducts, cancellation restores
# =====================================================================
Step 'A fixture: a material with material-stock + a supplier that has an outsource warehouse'
$fx = SqlRow "SELECT ws.warehouse_id, ws.material_id, wh.factory_id, (SELECT MIN(w2.id) FROM warehouse w2 WHERE w2.factory_id = wh.factory_id AND w2.warehouse_category='OUTSOURCE') FROM warehouse_stock ws JOIN warehouse wh ON wh.id = ws.warehouse_id WHERE ws.stock_form='MATERIAL' AND ws.material_id IS NOT NULL AND IFNULL(ws.quantity,0) >= 6 AND EXISTS (SELECT 1 FROM warehouse w2 WHERE w2.factory_id = wh.factory_id AND w2.warehouse_category='OUTSOURCE') ORDER BY (wh.warehouse_category <> 'OUTSOURCE') DESC, ws.id LIMIT 1"
$srcWh = [int]$fx[0]; $matId = [int]$fx[1]; $supId = [int]$fx[2]; $supWh = [int]$fx[3]
Write-Host ("FIXTURE-A: sourceWh=$srcWh material=$matId supplier=$supId outsourceWh=$supWh")
if ($srcWh -le 0 -or $supWh -le 0) {
  Info 'no usable fixture (material stock >= 6 in a warehouse whose owner has an outsource warehouse) -> CASE A skipped'
} else {
  $base = OnSite $supWh $matId
  Write-Host ('A1 base on-site=' + $base)
  $body = @{ supplierId = $supId; fromWarehouseId = $srcWh; returnType = 'REPAIR'; returnDate = '2026-09-27'; remark = 'GUARD-ONSITE-A'
             items = @(@{ materialId = $matId; quantity = 3 }) } | ConvertTo-Json -Depth 6
  $c = Invoke-RestMethod -Uri $API -Method Post -Headers $h -ContentType 'application/json' -Body $body
  Ok ($c.code -eq 200) ('A1 a REPAIR draft is created (' + $c.msg + ')')
  $docId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_material_return WHERE remark='GUARD-ONSITE-A'")
  Ok ($docId -gt 0) ('A1 draft id=' + $docId)

  $a = Invoke-RestMethod -Uri ($API + '/' + $docId + '/audit') -Method Put -Headers $h
  Write-Host ('A2 audit: code=' + $a.code + ' msg=' + $a.msg)
  Ok ($a.code -eq 200) 'A2 audit succeeded (sending material into the supplier outsource warehouse)'
  $afterAudit = OnSite $supWh $matId
  Write-Host ('A2 on-site=' + $afterAudit + ' (expected ' + ($base + 3) + ')')
  Ok ($afterAudit -eq ($base + 3)) 'A2 sending 3 units added 3 to the on-site row'

  # a return WITHOUT material-usage lines: this is the case the original bug leaked on
  $rb = @{ warehouseId = $srcWh; repairDate = '2026-09-27'; items = @(@{ materialId = $matId; quantity = 1 }); materials = @() } | ConvertTo-Json -Depth 6
  $r = Invoke-RestMethod -Uri ($API + '/' + $docId + '/repair-return') -Method Post -Headers $h -ContentType 'application/json' -Body $rb
  Write-Host ('A3 register: code=' + $r.code + ' msg=' + $r.msg)
  Ok ($r.code -eq 200) 'A3 a return without material-usage lines is accepted'
  $afterRegister = OnSite $supWh $matId
  Write-Host ('A3 on-site=' + $afterRegister + ' (expected ' + ($base + 2) + ')')
  Ok ($afterRegister -eq ($base + 2)) 'A3 registering deducted 1 from the on-site row'
  $recId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_material_return_repair WHERE return_order_id=$docId")
  Ok ($recId -gt 0) ('A3 repair record created (id=' + $recId + ')')
  Ok ([int](D (SqlOne "SELECT COALESCE(onsite_leg,1) FROM outsource_material_return_repair WHERE id=$recId")) -eq 1) 'A3 the record is flagged onsite_leg=1 (it DID deduct)'

  $rd = Invoke-RestMethod -Uri ($API + '/repair-return/' + $recId) -Method Delete -Headers $h
  Write-Host ('A4 cancel: code=' + $rd.code + ' msg=' + $rd.msg)
  Ok ($rd.code -eq 200) 'A4 the return record can be cancelled'
  $afterCancel = OnSite $supWh $matId
  Write-Host ('A4 on-site=' + $afterCancel + ' (expected ' + ($base + 3) + ')')
  Ok ($afterCancel -eq ($base + 3)) 'A4 cancelling restored the on-site row (CASE-A REGRESSION GUARD)'
  Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE id=$recId"))) -eq 0) 'A4 the record is gone (repeatable)'

  # un-audit is what used to fail hard with "on-site material insufficient" after a leaked cancellation
  $ua = Invoke-RestMethod -Uri ($API + '/' + $docId + '/un-audit') -Method Put -Headers $h
  Write-Host ('A5 un-audit: code=' + $ua.code + ' msg=' + $ua.msg)
  Ok ($ua.code -eq 200) 'A5 un-audit succeeded (no "on-site material insufficient")'
  Ok ((OnSite $supWh $matId) -eq $base) 'A5 un-audit rolled the on-site row back to base'
  # cleanup
  Invoke-RestMethod -Uri ($API + '/' + $docId + '/cancel') -Method Put -Headers $h | Out-Null
  Ok ([int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return WHERE id=$docId AND status='CANCELLED'")) -eq 1) 'A5 draft voided (cleanup)'
}

# =====================================================================
# CASE B: legacy document (never entered the on-site row) -> both legs must be no-ops
# =====================================================================
Step 'B fixture: an AUDITED REPAIR doc whose send never entered the on-site row (legacy) and room left'
$lb = SqlRow "SELECT h.id, i.outsource_material_id, h.from_warehouse_id, w2.id AS owh, IFNULL((SELECT SUM(x.quantity) FROM outsource_material_return_item x WHERE x.return_order_id=h.id AND x.outsource_material_id=i.outsource_material_id),0) AS sent, IFNULL((SELECT SUM(r.quantity) FROM outsource_material_return_repair r WHERE r.return_order_id=h.id AND r.material_id=i.outsource_material_id),0) AS ret, IFNULL((SELECT SUM(l.change_quantity) FROM warehouse_stock_log l WHERE l.stock_form='MATERIAL_REPAIR' AND l.change_type='MATERIAL_REPAIR_STOCK_IN' AND l.related_bill_id=h.id AND l.material_id=i.outsource_material_id),0) AS legs, w2.id FROM outsource_material_return h JOIN outsource_material_return_item i ON i.return_order_id=h.id JOIN warehouse w2 ON w2.factory_id = h.supplier_id AND w2.warehouse_category='OUTSOURCE' WHERE h.return_type='REPAIR' AND h.status='AUDITED' AND IFNULL(h.closed_flag,0)=0 AND i.outsource_material_id IS NOT NULL AND h.from_warehouse_id IS NOT NULL AND w2.id = (SELECT MIN(w3.id) FROM warehouse w3 WHERE w3.factory_id = h.supplier_id AND w3.warehouse_category='OUTSOURCE') AND IFNULL((SELECT SUM(x.quantity) FROM outsource_material_return_item x WHERE x.return_order_id=h.id AND x.outsource_material_id=i.outsource_material_id),0) > IFNULL((SELECT SUM(r.quantity) FROM outsource_material_return_repair r WHERE r.return_order_id=h.id AND r.material_id=i.outsource_material_id),0) AND IFNULL((SELECT SUM(l.change_quantity) FROM warehouse_stock_log l WHERE l.stock_form='MATERIAL_REPAIR' AND l.change_type='MATERIAL_REPAIR_STOCK_IN' AND l.related_bill_id=h.id AND l.material_id=i.outsource_material_id),0) < IFNULL((SELECT SUM(x.quantity) FROM outsource_material_return_item x WHERE x.return_order_id=h.id AND x.outsource_material_id=i.outsource_material_id),0) AND IFNULL((SELECT SUM(ws.quantity) FROM warehouse_stock ws WHERE ws.warehouse_id=w2.id AND ws.material_id=i.outsource_material_id AND ws.stock_form='MATERIAL_REPAIR'),0) = 0 ORDER BY h.id LIMIT 1"
$lDoc = [int]$lb[0]; $lMat = [int]$lb[1]; $lWh = [int]$lb[2]; $lOwh = [int]$lb[3]
Write-Host ("FIXTURE-B: doc=$lDoc material=$lMat returnWh=$lWh outsourceWh=$lOwh")
if ($lDoc -le 0 -or $lOwh -le 0) {
  Info 'no legacy AUDITED repair doc with an empty on-site row -> CASE B skipped (coverage gap)'
} else {
  $b = OnSite $lOwh $lMat
  Ok ($b -eq 0) ('B1 the legacy doc has an empty on-site row (base=' + $b + ')')
  $rb2 = @{ warehouseId = $lWh; repairDate = '2026-09-27'; items = @(@{ materialId = $lMat; quantity = 1 }); materials = @() } | ConvertTo-Json -Depth 6
  $r2 = Invoke-RestMethod -Uri ($API + '/' + $lDoc + '/repair-return') -Method Post -Headers $h -ContentType 'application/json' -Body $rb2
  Write-Host ('B2 register: code=' + $r2.code + ' msg=' + $r2.msg)
  Ok ($r2.code -eq 200) 'B2 a return on a legacy doc is accepted (the deduction leg is skipped)'
  Ok ((OnSite $lOwh $lMat) -eq 0) 'B2 the skipped deduction left the on-site row alone'
  $rec2 = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_material_return_repair WHERE return_order_id=$lDoc")
  Ok ([int](D (SqlOne "SELECT COALESCE(onsite_leg,1) FROM outsource_material_return_repair WHERE id=$rec2")) -eq 0) 'B2 the record is flagged onsite_leg=0 (it did NOT deduct)'
  $rd2 = Invoke-RestMethod -Uri ($API + '/repair-return/' + $rec2) -Method Delete -Headers $h
  Write-Host ('B3 cancel: code=' + $rd2.code + ' msg=' + $rd2.msg)
  Ok ($rd2.code -eq 200) 'B3 the record can still be cancelled'
  Ok ((OnSite $lOwh $lMat) -eq 0) 'B3 cancelling did NOT invent on-site units (CASE-B REGRESSION GUARD)'
}

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-repair-cancel-onsite (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
