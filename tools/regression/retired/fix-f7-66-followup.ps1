# RETIRED 2026-09-24 -- manual material-delivery doc (outsource/delivery) was removed.
# The list/add pages were deleted and POST/PUT create+update endpoints now always reject;
# the replacement is the material warehouse-move doc: /inventory/material-move (backend
# /api/inventory/material-move), covered by verify-material-move.ps1. This script targets the
# removed entry, so it is kept for history only and must NOT be re-added to any suite.

# fix-f7-66-followup.ps1  (follow-up to the F7-66 correction, user-approved items 1 & 2)
#
# ITEM 1: the 4 duplicate receipts (73/74/75/76) were left as DRAFT by the main correction; they
#         occupy the in-flight quota, so cancel them (business API, keeps the audit trail).
# ITEM 2: reversing the over-receipt drove warehouse 74 (our own material warehouse) NEGATIVE
#         (mat 33 -> -157, mat 35 -> -300) because the inflated stock had already been transferred
#         to the factories. Make it whole with a normal TRANSFER document (outsource warehouse 68 ->
#         our warehouse 74) for exactly the negative amounts -- this cancels the inflation on BOTH
#         sides instead of adjusting only one. Amounts: mat 33 x157, mat 35 x300.
#
# Everything goes through the business API; the transfer document can be un-audited/cancelled later
# if the business wants a different treatment.
#
# DEPENDENCY: backend on 8080; lin/123 login. ASCII-only on purpose (PS 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$env:MYSQL_PWD = 'root'
$script:fails = 0
function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) { $v = Sql $sql; if ($v -eq '') { return '' }; return ($v -split "`n")[0].Trim() }
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }
function CodeOf($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function MsgOf($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }

$body = '{"username":"lin","password":"123","companyId":1}'
try {
  $login = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($body))
} catch { Write-Output ("LOGIN EX " + $_.Exception.Message); exit 1 }
$H = @{ Authorization = [string]$login.data.token }
function Api([string]$method, [string]$url, $payload) {
  try {
    if ($null -eq $payload) { return Invoke-RestMethod -Uri $url -Method $method -Headers $H }
    $json = ConvertTo-Json -InputObject $payload -Depth 8
    return Invoke-RestMethod -Uri $url -Method $method -Headers $H `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    $code = -1
    try { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } } catch { }
    return [pscustomobject]@{ code = $code; msg = "HTTPEX " + $_.Exception.Message; data = $null }
  }
}

$FROM_WH = 68   # 测试加工厂A3委外仓库 (OUTSOURCE, mat33=900 mat35=900)
$TO_WH   = 74   # 自有物料一号仓 (INVENTORY/AUXILIARY, mat33=-157 mat35=-300)
$today = (Get-Date).ToString('yyyy-MM-dd')

Write-Output '=== item 1) cancel the 4 duplicate receipt drafts (73/74/75/76) ==='
foreach ($id in @(73, 74, 75, 76)) {
  $before = SqlOne "SELECT status FROM outsource_delivery WHERE id=$id"
  $r = Api 'Put' "$BASE/outsource/delivery/$id/cancel" $null
  $after = SqlOne "SELECT status FROM outsource_delivery WHERE id=$id"
  if ((CodeOf $r) -eq '200' -and $after -eq 'CANCELLED') {
    Ok ("delivery $id cancelled ($before -> $after); no longer occupies the in-flight quota")
  } else {
    Bad ("delivery $id cancel failed: code=" + (CodeOf $r) + " msg=" + (MsgOf $r) + " status=$after")
  }
}

Write-Output ''
Write-Output '=== item 2) make warehouse 74 whole via a TRANSFER (68 -> 74) ==='
Write-Output '  -- before --'
Write-Output (Sql "SELECT warehouse_id,material_id,quantity FROM warehouse_stock WHERE material_id IN (33,34,35) AND warehouse_id IN (68,74) ORDER BY material_id,warehouse_id")

$rNew = Api 'Post' "$BASE/outsource/delivery" @{
  deliveryType = 'TRANSFER'
  fromWarehouseId = $FROM_WH
  toWarehouseId = $TO_WH
  deliveryDate = $today
  remark = 'F7-66 correction: reverse over-receipt inflation (mat33 x157 + mat35 x300)'
  items = @(
    @{ materialId = 33; quantity = 157; qualityType = 'GOOD'; unit = 'PCS' },
    @{ materialId = 35; quantity = 300; qualityType = 'GOOD'; unit = 'PCS' }
  )
}
if ((CodeOf $rNew) -ne '200') {
  Bad ("transfer document creation failed: " + (MsgOf $rNew))
} else {
  $tid = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_delivery")
  Ok ("transfer draft created: id=$tid (" + (SqlOne "SELECT CONCAT(code,'|',status,'|',from_warehouse_id,'->',to_warehouse_id) FROM outsource_delivery WHERE id=$tid") + ")")
  $rAud = Api 'Put' "$BASE/outsource/delivery/$tid/audit" $null
  if ((CodeOf $rAud) -eq '200') {
    Ok ("transfer audited: stock moved")
  } else {
    Bad ("transfer audit failed: " + (MsgOf $rAud))
  }
}

Write-Output ''
Write-Output '  -- after --'
Write-Output (Sql "SELECT warehouse_id,material_id,quantity FROM warehouse_stock WHERE material_id IN (33,34,35) AND warehouse_id IN (68,74) ORDER BY material_id,warehouse_id")

Write-Output ''
Write-Output '=== verification ==='
$neg = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE quantity < 0")
if ($neg -eq 0) { Ok 'no negative stock row remains' } else { Bad "$neg warehouse_stock row(s) still negative" }

$wh74Negative = [int](SqlOne "SELECT COUNT(*) FROM warehouse_stock WHERE warehouse_id=$TO_WH AND quantity < 0")
if ($wh74Negative -eq 0) { Ok "warehouse $TO_WH has no negative row" } else { Bad "warehouse $TO_WH still has $wh74Negative negative row(s)" }

$over = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order_item WHERE IFNULL(received_quantity,0) > IFNULL(order_quantity,0)")
if ($over -eq 0) { Ok 'invariant still holds: received_quantity > order_quantity rows = 0' } else { Bad "invariant broken: $over row(s)" }

$draftLeft = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id IN (73,74,75,76) AND status='DRAFT'")
if ($draftLeft -eq 0) { Ok 'no duplicate receipt draft left in DRAFT' } else { Bad "$draftLeft duplicate draft(s) still DRAFT" }

Write-Output '  -- final stock of materials 33/34/35 by warehouse --'
Write-Output (Sql "SELECT warehouse_id,material_id,quantity FROM warehouse_stock WHERE material_id IN (33,34,35) ORDER BY material_id,warehouse_id")
Write-Output '  -- documents 73..80 + the new transfer --'
Write-Output (Sql "SELECT id,code,delivery_type,status,source_order_id FROM outsource_delivery WHERE id BETWEEN 73 AND 80 OR code LIKE 'DEL-2026091900%' ORDER BY id")

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures)' }
else { Write-Output ("RESULT FAIL: " + $script:fails + " step(s) failed") }
