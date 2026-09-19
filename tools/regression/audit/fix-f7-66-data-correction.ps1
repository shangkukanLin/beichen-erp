# fix-f7-66-data-correction.ps1  (one-off data correction for F7-66, user-approved option A)
#
# WHY: 8 material-order item rows carry received_quantity > order_quantity (1900 units / 18800 CNY
# of payables) because receiving had no "received <= ordered" cap. The code fix lands the cap;
# this script repairs the existing data THROUGH THE BUSINESS API (no hand-written UPDATE/DELETE on
# stock or payables), so every step leaves audit trail + stock logs.
#
# APPROVED PLAN (option A):
#   4 duplicate receipts (73/74/75/76)  -> simply un-audit (received 600->300 etc, payable voided)
#   4 over-quantity receipts (77/78/79/80) -> "un-audit, fix the qty, re-audit".
#     NOTE: the literal "edit the qty" step is NOT usable here -- DeliveryController.parseItems never
#     reads `itemId`, so a PUT would null out outsource_delivery_item.item_id and the re-audit would
#     silently skip the order-quantity write-back (the F7-68 family defect this run discovered).
#     Equivalent business path used instead: un-audit -> cancel the old draft -> receive the
#     correct quantity -> audit. Same end state, same trails.
#
# Backup: audit-backup-f7-66-20260919.sql (mysqldump of the 7 touched tables) taken before running.
# DEPENDENCY: backend on 8080; lin/123 login.

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

$WH = 74
# duplicate receipts: un-audit only (order item row, receipt qty, order qty)
$REPEAT = @(
  @{ del = 73; item = 21; qty = 300; order = 16 },
  @{ del = 74; item = 22; qty = 300; order = 17 },
  @{ del = 75; item = 23; qty = 300; order = 18 },
  @{ del = 76; item = 24; qty = 200; order = 19 }
)
# over-quantity receipts: un-audit -> cancel -> receive(correct qty) -> audit
$OVER = @(
  @{ del = 77; item = 25; order = 20; good = 300 },
  @{ del = 78; item = 27; order = 21; good = 200 },
  @{ del = 79; item = 29; order = 22; good = 200 },
  @{ del = 80; item = 31; order = 23; good = 200 }
)

Write-Output '=== step 1) 4 duplicate receipts: un-audit ==='
foreach ($r in $REPEAT) {
  $before = SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$($r.item)"
  $resp = Api 'Put' "$BASE/outsource/material-order/delivery/$($r.del)/un-audit" $null
  $after = SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$($r.item)"
  $st = SqlOne "SELECT status FROM outsource_delivery WHERE id=$($r.del)"
  if ((CodeOf $resp) -eq '200' -and [decimal]$after -eq [decimal]$r.qty -and $st -eq 'DRAFT') {
    Ok ("delivery $($r.del) un-audited: item $($r.item) received $before -> $after (= ordered $($r.qty)), doc status=$st")
  } else {
    Bad ("delivery $($r.del): code=" + (CodeOf $resp) + " msg=" + (MsgOf $resp) + " received $before->$after status=$st")
  }
}

Write-Output ''
Write-Output '=== step 2) 4 over-quantity receipts: un-audit -> cancel -> receive(good qty) -> audit ==='
foreach ($r in $OVER) {
  $b0 = SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$($r.item)"
  $resp1 = Api 'Put' "$BASE/outsource/material-order/delivery/$($r.del)/un-audit" $null
  $a1 = SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$($r.item)"
  if ((CodeOf $resp1) -ne '200') { Bad ("delivery $($r.del) un-audit failed: " + (MsgOf $resp1)); continue }
  Ok ("delivery $($r.del) un-audited: item $($r.item) received $b0 -> $a1")

  $resp2 = Api 'Put' "$BASE/outsource/delivery/$($r.del)/cancel" $null
  if ((CodeOf $resp2) -eq '200') {
    Ok ("old over-quantity draft $($r.del) cancelled (releases the duplicate-submit guard)")
  } else {
    Bad ("cancel of $($r.del) failed: " + (MsgOf $resp2)); continue
  }

  $resp3 = Api 'Post' "$BASE/outsource/material-order/$($r.order)/receive" @{
    warehouseId = $WH; force = $true; items = @(@{ itemId = $r.item; quantity = $r.good }) }
  if ((CodeOf $resp3) -ne '200') { Bad ("receive for order $($r.order) failed: " + (MsgOf $resp3)); continue }
  $newId = [int]$resp3.data
  Ok ("new receipt draft for order $($r.order): id=$newId qty=$($r.good)")

  $resp4 = Api 'Put' "$BASE/outsource/material-order/delivery/$newId/audit" $null
  $a4 = SqlOne "SELECT received_quantity FROM outsource_material_order_item WHERE id=$($r.item)"
  if ((CodeOf $resp4) -eq '200' -and [decimal]$a4 -eq [decimal]$r.good) {
    Ok ("delivery $newId audited: item $($r.item) received -> $a4 (= ordered $($r.good))")
  } else {
    Bad ("audit of $newId failed: code=" + (CodeOf $resp4) + " msg=" + (MsgOf $resp4) + " received=$a4")
  }
}

Write-Output ''
Write-Output '=== step 3) verification ==='
$over = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order_item WHERE IFNULL(received_quantity,0) > IFNULL(order_quantity,0)")
if ($over -eq 0) { Ok 'invariant restored: received_quantity > order_quantity rows = 0' }
else { Bad "invariant still violated: $over row(s)" }

Write-Output '  -- order items after correction --'
Write-Output (Sql "SELECT id,order_id,outsource_material_id,order_quantity,received_quantity,defect_returned_qty FROM outsource_material_order_item ORDER BY id")
Write-Output '  -- payables after correction --'
Write-Output (Sql "SELECT status,COUNT(*) c,SUM(amount) amt FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' GROUP BY status")
Write-Output '  -- warehouse 74 stock (33/34/35) --'
Write-Output (Sql "SELECT material_id,quantity FROM warehouse_stock WHERE warehouse_id=$WH AND material_id IN (33,34,35) ORDER BY material_id")
Write-Output '  -- the 8 corrected documents --'
Write-Output (Sql "SELECT id,code,status,source_order_id FROM outsource_delivery WHERE id BETWEEN 73 AND 80 ORDER BY id")
Write-Output '  -- new drafts created by this correction --'
Write-Output (Sql "SELECT id,code,status,source_order_id FROM outsource_delivery WHERE source_order_id IN (20,21,22,23) ORDER BY id")

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures) -- data corrected via business API' }
else { Write-Output ("RESULT FAIL: " + $script:fails + " step(s) failed -- inspect before retrying") }
