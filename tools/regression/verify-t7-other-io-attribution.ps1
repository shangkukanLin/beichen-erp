# T7 (2026-09-18) verification: other-io detail lines must be attributable to a product,
# and the stock-write layer must reject rows with neither product nor material.
#   T7-1 create other-io without productId        -> rejected (was: saved + audited + ghost stock row)
#   T7-2 no new doc / no new ghost stock row
#   T7-3 normal other-io IN audit/un-audit/cancel -> still works (regression)
#   T7-4 legacy draft doc with NULL product line  -> audit rejected
#   T7-5 stock-layer backstop via warehouse-move  -> rejected with the "must specify product/material" message
#   T7-6 ghost stock rows count unchanged
# API + DB cross-check, rerunnable, ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step([string]$m) { Write-Host ('--- ' + $m) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  $ls = ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim() -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null | Out-Null }
function LastId([string]$t) { return [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM $t") }

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
function ApiRaw($method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers $h
  } catch { return $_.ErrorDetails.Message }
}
function Msg($r) { if ($null -eq $r) { return '<null>' }; if ($r -is [string]) { return $r }; return ('code=' + $r.code + ' ' + $r.msg) }

$prodId = [int](SqlOne "SELECT COALESCE(MAX(id),0) FROM product")
# Chinese terms built from code points so this file stays ASCII-only (PS 5.1 + no BOM = ANSI)
$CN_PROD = [string][char]0x4EA7 + [string][char]0x54C1   # product
$CN_MAT  = [string][char]0x7269 + [string][char]0x6599   # material
$whId   = [int](SqlOne "SELECT COALESCE(MIN(warehouse_id),0) FROM warehouse_stock WHERE product_id=$prodId AND quantity > 0")
$wh2    = [int](SqlOne "SELECT COALESCE(MIN(id),0) FROM warehouse WHERE warehouse_category='INVENTORY'")
$today  = (Get-Date -Format 'yyyy-MM-dd')
Write-Host ("[SEED] product=$prodId warehouse=$whId warehouse2=$wh2")
if ($prodId -le 0 -or $whId -le 0) { Write-Host 'FAIL prerequisites missing'; exit 1 }

$ghost0 = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NULL AND material_id IS NULL")
$io0    = D (SqlOne "SELECT COUNT(*) FROM inventory_other_io")
Write-Host ("[BASE] ghost stock rows=$ghost0  other-io docs=$io0")

# =====================================================================
Step 'T7-1 create other-io WITHOUT productId -> must be rejected'
$bad = ApiRaw 'POST' '/inventory/other' ('{"warehouseId":' + $whId + ',"ioType":"IN","ioDate":"' + $today + '","remark":"AUDIT-T7-NOPROD","items":[{"quantity":5,"qualityType":"A"}]}')
Write-Host ('create (no product) -> ' + (Msg $bad))
Ok ((Msg $bad) -match 'code=500') 'T7-1 request rejected'
Ok ((Msg $bad) -match [regex]::Escape($CN_PROD)) 'T7-1 rejection message mentions the product requirement'
Ok ((D (SqlOne "SELECT COUNT(*) FROM inventory_other_io")) -eq $io0) 'T7-1 no document was created'

Step 'T7-2 (control) same payload WITHOUT items -> rejected'
$bad2 = ApiRaw 'POST' '/inventory/other' ('{"warehouseId":' + $whId + ',"ioType":"IN","ioDate":"' + $today + '","items":[]}')
Write-Host ('create (empty items) -> ' + (Msg $bad2))
Ok ((Msg $bad2) -match 'code=500') 'T7-2 empty items rejected'

# =====================================================================
Step 'T7-3 normal other-io IN still works (regression)'
$q0 = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId")
$body = '{"warehouseId":' + $whId + ',"ioType":"IN","ioDate":"' + $today + '","remark":"AUDIT-T7-OK","items":[{"productId":' + $prodId + ',"quantity":2,"qualityType":"A"}]}'
$r3 = ApiRaw 'POST' '/inventory/other' $body
Write-Host ('create -> ' + (Msg $r3))
$id3 = LastId 'inventory_other_io'
Ok ($id3 -gt $io0) ('T7-3 doc created id=' + $id3)
Ok ((D (SqlOne "SELECT COUNT(*) FROM inventory_other_io_item WHERE other_io_id=$id3 AND product_id=$prodId")) -eq 1) 'T7-3 item persisted with productId'
Ok ((Msg (ApiRaw 'PUT' "/inventory/other/$id3/audit" '')) -match 'code=200') 'T7-3 audit ok'
$q1 = D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId")
Ok (($q1 -eq ($q0 + 2))) ('T7-3 stock +2 after audit (' + $q0 + ' -> ' + $q1 + ')')
$code3 = SqlOne ("SELECT code FROM inventory_other_io WHERE id=" + $id3)
Ok ((D (SqlOne ("SELECT COUNT(*) FROM warehouse_stock_log WHERE related_bill_no='" + $code3 + "' AND related_bill_id=" + $id3))) -ge 1) ('T7-3 stock log written with bill no/id (' + $code3 + ' / ' + $id3 + ')')
Ok ((Msg (ApiRaw 'PUT' "/inventory/other/$id3/un-audit" '')) -match 'code=200') 'T7-3 un-audit ok'
Ok ((D (SqlOne "SELECT COALESCE(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=$whId AND product_id=$prodId")) -eq $q0) 'T7-3 stock restored'
Ok ((Msg (ApiRaw 'PUT' "/inventory/other/$id3/cancel" '')) -match 'code=200') 'T7-3 cancel ok'

# =====================================================================
Step 'T7-4 legacy DRAFT doc with a NULL-product line -> audit must be rejected'
# unique code per run, otherwise the id is looked up from a previous run's row
$code4 = 'QT-T7-L' + (Get-Date -Format 'HHmmss')
SqlExec ("INSERT INTO inventory_other_io (code,warehouse_id,io_type,io_date,status,remark,company_id) VALUES ('" + $code4 + "'," + $whId + ",'IN','" + $today + "','DRAFT','AUDIT-T7-LEGACY',1)")
$ioLegacy = [int](SqlOne ("SELECT id FROM inventory_other_io WHERE code='" + $code4 + "'"))
SqlExec ("INSERT INTO inventory_other_io_item (other_io_id,product_id,quality_type,quantity,company_id) VALUES (" + $ioLegacy + ",NULL,'A',5,1)")
$r4 = ApiRaw 'PUT' "/inventory/other/$ioLegacy/audit" ''
Write-Host ('audit legacy -> ' + (Msg $r4))
Ok ((Msg $r4) -match 'code=500') 'T7-4 legacy audit rejected'
Ok ((SqlOne "SELECT status FROM inventory_other_io WHERE id=$ioLegacy") -eq 'DRAFT') 'T7-4 doc stays DRAFT'
SqlExec ("UPDATE inventory_other_io SET status='CANCELLED' WHERE id=$ioLegacy")

# =====================================================================
Step 'T7-5 stock-write layer backstop (warehouse-move with NULL product)'
$code5 = 'YC-T7-L' + (Get-Date -Format 'HHmmss')
SqlExec ("INSERT INTO inventory_warehouse_move (code,from_warehouse_id,to_warehouse_id,move_date,status,remark,company_id) VALUES ('" + $code5 + "'," + $whId + "," + $wh2 + ",'" + $today + "','DRAFT','AUDIT-T7-BACKSTOP',1)")
$mvId = [int](SqlOne ("SELECT id FROM inventory_warehouse_move WHERE code='" + $code5 + "'"))
SqlExec ("INSERT INTO inventory_warehouse_move_item (move_id,product_id,quality_type,quantity,company_id) VALUES (" + $mvId + ",NULL,'A',1,1)")
$r5 = ApiRaw 'PUT' "/inventory/warehouse-move/$mvId/audit" ''
Write-Host ('audit move -> ' + (Msg $r5))
Ok ((Msg $r5) -match 'code=500') 'T7-5 move audit rejected by the stock layer'
Ok (((Msg $r5) -match [regex]::Escape($CN_PROD)) -and ((Msg $r5) -match [regex]::Escape($CN_MAT))) 'T7-5 message is the stock-layer guard (product/material must be specified)'
SqlExec ("DELETE FROM inventory_warehouse_move_item WHERE move_id=$mvId")
SqlExec ("DELETE FROM inventory_warehouse_move WHERE id=$mvId")
# clean any leftovers from earlier runs (unique-prefix rows only)
SqlExec "DELETE FROM inventory_other_io_item WHERE other_io_id IN (SELECT id FROM inventory_other_io WHERE code LIKE 'QT-T7-L%')"
SqlExec "DELETE FROM inventory_other_io WHERE code LIKE 'QT-T7-L%'"
SqlExec "DELETE FROM inventory_warehouse_move_item WHERE move_id IN (SELECT id FROM inventory_warehouse_move WHERE code LIKE 'YC-T7-L%')"
SqlExec "DELETE FROM inventory_warehouse_move WHERE code LIKE 'YC-T7-L%'"

# =====================================================================
Step 'T7-6 no new ghost stock row'
$ghost1 = D (SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE product_id IS NULL AND material_id IS NULL")
Ok (($ghost1 -eq $ghost0)) ('T7-6 ghost rows unchanged (' + $ghost0 + ' -> ' + $ghost1 + ')')
Ok ((D (SqlOne "SELECT COUNT(*) FROM inventory_other_io WHERE remark LIKE 'AUDIT-T7-%' AND status<>'CANCELLED'")) -eq 0) 'T7-6 all T7 test docs cleaned up'
Ok ((D (SqlOne "SELECT COUNT(*) FROM inventory_other_io_item i JOIN inventory_other_io d ON d.id=i.other_io_id WHERE i.product_id IS NULL AND d.remark LIKE 'AUDIT-T7-%'")) -eq 0) 'T7-6 no NULL-product line left in T7 test docs'
$legacyNull = D (SqlOne "SELECT COUNT(*) FROM inventory_other_io_item WHERE product_id IS NULL")
Write-Host ('INFO legacy NULL-product lines still in DB (dirty data created BEFORE this fix) = ' + $legacyNull)

Write-Host ''
Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' t7 attribution  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ')')
if ($script:FAIL -ne 0) { exit 1 }
