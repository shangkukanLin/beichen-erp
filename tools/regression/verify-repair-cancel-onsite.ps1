# Symmetry guard (2026-09-27): cancelling a material-side repair return must RESTORE the on-site row.
#   repairReturn() deducts the on-site row (warehouse_stock form=MATERIAL_REPAIR, supplier outsource warehouse)
#   UNCONDITIONALLY (allocateOnSiteRepair), so cancelRepairReturn() must restore it UNCONDITIONALLY too.
#   Bug found while verifying the repair-material scope rule: the restore used to sit inside
#   `if (!mats.isEmpty())` => a return WITHOUT any material-usage lines leaked the on-site quantity,
#   and the later un-audit failed with "on-site material insufficient" (reproduced by ui-e2e-15 S8).
# PURE ASCII.
$ErrorActionPreference = 'Continue'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }

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
function D([string]$s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$lg = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
Ok ($lg.code -eq 200 -and $lg.data.token) 'login ok'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-repair-cancel-onsite (no token)'; exit 1 }

Step 'fixture: audited REPAIR material return with room left'
$fx = @(((SqlRaw "SELECT h.id, i.outsource_material_id, h.from_warehouse_id, h.supplier_id FROM outsource_material_return h JOIN outsource_material_return_item i ON i.return_order_id = h.id WHERE h.return_type='REPAIR' AND h.status='AUDITED' AND IFNULL(h.closed_flag,0)=0 AND (SELECT IFNULL(SUM(i2.quantity),0) FROM outsource_material_return_item i2 WHERE i2.return_order_id=h.id AND i2.outsource_material_id=i.outsource_material_id) > (SELECT IFNULL(SUM(r.quantity),0) FROM outsource_material_return_repair r WHERE r.return_order_id=h.id AND r.material_id=i.outsource_material_id) ORDER BY h.id DESC LIMIT 1") -split "`n")[1] -split "`t" | ForEach-Object { "$_".Trim() })
$docId = [int]$fx[0]; $matId = [int]$fx[1]; $whId = [int]$fx[2]; $supId = [int]$fx[3]
Write-Host ("FIXTURE: doc=$docId material=$matId returnWh=$whId supplier=$supId")
if ($docId -le 0 -or $whId -le 0) { Write-Host 'INFO no usable fixture -> skipped (coverage gap)'; Write-Host 'RESULT PASS verify-repair-cancel-onsite (skipped)'; exit 0 }

# the supplier's outsource warehouse == where the on-site row lives
$supWh = [int](SqlOne "SELECT id FROM warehouse WHERE factory_id=$supId AND warehouse_category='OUTSOURCE' ORDER BY id LIMIT 1")
Ok ($supWh -gt 0) ('supplier outsource warehouse resolved (id=' + $supWh + ')')
$onsite = { D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$supWh AND material_id=$matId AND stock_form='MATERIAL_REPAIR'") }

Step 'register a return WITHOUT any material-usage line, then cancel it -> the on-site row must come back'
$base = & $onsite
Write-Host ('BASE on-site=' + $base)
$body = @{ warehouseId = $whId; repairDate = '2026-09-27'; items = @(@{ materialId = $matId; quantity = 1 }); materials = @() } | ConvertTo-Json -Depth 6
$r = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/material-return/$docId/repair-return" -Method Post -Headers $h -ContentType 'application/json' -Body $body
Write-Host ('register: code=' + $r.code + ' msg=' + $r.msg)
Ok ($r.code -eq 200) 'a return without material-usage lines is accepted'
$afterRegister = & $onsite
Write-Host ('AFTER-REGISTER on-site=' + $afterRegister)
Ok ($afterRegister -eq ($base - 1)) 'registering deducted 1 from the on-site row'
$recId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM outsource_material_return_repair WHERE return_order_id=$docId")
Ok ($recId -gt 0) ('repair record created (id=' + $recId + ')')
$rd = Invoke-RestMethod -Uri "http://localhost:8080/api/outsource/material-return/repair-return/$recId" -Method Delete -Headers $h
Write-Host ('cancel: code=' + $rd.code + ' msg=' + $rd.msg)
Ok ($rd.code -eq 200) 'the return record can be cancelled'
$afterCancel = & $onsite
Write-Host ('AFTER-CANCEL on-site=' + $afterCancel)
Ok ($afterCancel -eq $base) 'cancelling restored the on-site row (SYMMETRY - this is the regression guard)'
Ok (([int](D (SqlOne "SELECT COUNT(*) FROM outsource_material_return_repair WHERE id=$recId"))) -eq 0) 'the record is gone (repeatable)'

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-repair-cancel-onsite (FAIL=' + $script:fail + ')')
if ($script:fail -ne 0) { exit 1 }
