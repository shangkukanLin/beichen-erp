# verify-material-return-target.ps1 (2026-09-21) -- API-level guard for the material-return counterpart rule.
#
#   USER RULE: "物料退货只允许 辅料商 + 供应商，排除供货商" (a material return may only go to a 辅料商/供应商,
#   never to a 供货商 = supplier_type_ref.type_code 'product' = 成品商). The page filters its dropdowns with
#   excludeSupplierType=product, but the RULE lives in the service layer (create / update), so this script
#   calls the API directly:
#     1) a VENDOR (type=product) as supplierId  -> must be refused, and nothing may be written
#     2) an allowed supplier (辅料商/方案商/加工厂) -> must be accepted (draft), then deleted again
#   READ-ONLY apart from a draft created + deleted in the same run => rerunnable, leaves no trace.
#   ASCII ONLY (PS 5.1 mangles UTF-8 without a BOM: keep every expectation out of this file).
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | Select-Object -First 1)
}
function SqlRow([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  $line = (@($o) | Select-Object -First 1)
  if (-not $line) { return @() }
  return (@(($line -split "`t") | ForEach-Object { "$_".Trim() }))
}

# ---------- login (sa-token: header is Authorization, NO "Bearer " prefix) ----------
$lg = Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$tk = $lg.data.token
$H = @{ Authorization = $tk }
Ok ([bool]$tk) 'logged in'

# ---------- fixtures ----------
$fx = SqlRow "SELECT st.warehouse_id, st.material_id FROM warehouse_stock st WHERE st.material_id IS NOT NULL AND st.quality_type='GOOD' AND st.quantity >= 2 ORDER BY st.quantity DESC LIMIT 1"
$whId = [int]$fx[0]; $matId = [int]$fx[1]
$allowedId = [int](SqlOne "SELECT s.id FROM supplier s WHERE NOT EXISTS (SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id = s.id AND r.type_code='product') ORDER BY s.id LIMIT 1")
$vendorId = [int](SqlOne "SELECT s.id FROM supplier s WHERE EXISTS (SELECT 1 FROM supplier_type_ref r WHERE r.supplier_id = s.id AND r.type_code='product') ORDER BY s.id LIMIT 1")
Write-Host ('FIXTURE warehouse=' + $whId + ' material=' + $matId + ' allowed-supplier=' + $allowedId + ' vendor=' + $vendorId)
Ok (($whId -gt 0) -and ($matId -gt 0) -and ($allowedId -gt 0) -and ($vendorId -gt 0)) 'fixture derived (stock + one allowed supplier + one vendor)'

# ---------- 1) NEGATIVE: a vendor must be refused ----------
$beforeRows = [int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return')
$beforeVendorRows = [int](SqlOne ("SELECT COUNT(*) FROM outsource_material_return WHERE supplier_id=$vendorId"))
$badBody = @{
  supplierId = $vendorId; fromWarehouseId = $whId; returnDate = '2026-09-21'; returnType = 'REFUND'
  remark = 'negative: vendor as material-return target'
  items = @(@{ materialId = $matId; unit = 'PCS'; quantity = 1 })
} | ConvertTo-Json -Depth 6
$rc = -1; $rmsg = ''
try {
  $r = Invoke-RestMethod -Uri ($base + '/outsource/material-return') -Method Post -Headers $H -ContentType 'application/json' -Body $badBody
  $rc = [int]$r.code; $rmsg = "$($r.msg)"
} catch {
  $body = $_.ErrorDetails.Message
  if ($body) { $rmsg = $body }
  $rc = 500
}
Write-Host ('VENDOR-TARGET: code=' + $rc + ' msg=' + $rmsg)
Ok ($rc -ne 200) 'a vendor cannot be the target of a material return'
Ok ($rmsg -match '[\u4e00-\u9fa5]') 'the rejection is explained in words'
Ok ([int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return') -eq $beforeRows) 'the rejected request wrote no document'
Ok ([int](SqlOne ("SELECT COUNT(*) FROM outsource_material_return WHERE supplier_id=$vendorId")) -eq $beforeVendorRows) 'no draft was left pointing at the vendor'

# ---------- 2) POSITIVE: an allowed supplier is accepted, then removed again ----------
$goodBody = @{
  supplierId = $allowedId; fromWarehouseId = $whId; returnDate = '2026-09-21'; returnType = 'REFUND'
  remark = 'verify-material-return-target (deleted below)'
  items = @(@{ materialId = $matId; unit = 'PCS'; quantity = 1 })
} | ConvertTo-Json -Depth 6
$okCode = -1
try {
  $r2 = Invoke-RestMethod -Uri ($base + '/outsource/material-return') -Method Post -Headers $H -ContentType 'application/json' -Body $goodBody
  $okCode = [int]$r2.code
} catch { $okCode = 500 }
Ok ($okCode -eq 200) ('an allowed supplier is accepted (code=' + $okCode + ')')
$newId = [int](SqlOne 'SELECT COALESCE(MAX(id),0) FROM outsource_material_return')
if ($okCode -eq 200) {
  Ok ([int](SqlOne ("SELECT supplier_id FROM outsource_material_return WHERE id=$newId AND status='DRAFT'")) -eq $allowedId) 'the created draft keeps the allowed supplier'
  try { Invoke-RestMethod -Uri ($base + '/outsource/material-return/' + $newId) -Method Delete -Headers $H | Out-Null } catch { }
  Ok ([int](SqlOne ("SELECT COUNT(*) FROM outsource_material_return WHERE id=$newId")) -eq 0) 'the probe draft was deleted again'
}
Ok ([int](SqlOne 'SELECT COUNT(*) FROM outsource_material_return') -eq $beforeRows) 'row count is back to the starting value'

if ($fail -eq 0) { Write-Host 'RESULT PASS material-return target rule (vendor refused / allowed supplier accepted)' } else { Write-Host ('RESULT FAIL count=' + $fail); exit 1 }
