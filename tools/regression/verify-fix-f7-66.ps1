# verify-fix-f7-66.ps1  (regression for the F7-66 fix: receiving must not exceed the ordered quantity)
#
# F7-66 (P0): MaterialOrderServiceImpl.receive() only filtered qty > 0 and
# DeliveryServiceImpl.auditMaterialDelivery() accumulated unconditionally, so a user could receive
# more than was ordered. Live data already showed 8 item rows with received_quantity >
# order_quantity (1900 units / 18800 CNY of payables). The fix adds two independent guards:
#   1) receive():      qty <= order_quantity - received_quantity - in-flight DRAFT qty
#   2) audit():        received + qty <= order_quantity   (second line of defence)
# plus: F7-67 -- audit() now THROWS when the delivery item points at a missing order item row
# (previously it silently `continue`d while still posting stock + payable).
# plus: duplicate-submit guard now reports an error instead of silently reusing the draft row.
#
# Assertions are paired with positive controls; the probe order is created and removed by this
# script and every touched table is re-counted against the baseline. Nothing is left audited
# unless the test itself audits it (and that always fails, so the transaction rolls back).
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
function D($s) {
  if ($null -eq $s -or "$s" -eq '') { return [decimal]0 }
  try { return [decimal]$s } catch { return [decimal]-999999 }
}
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

$PROBE = 'MO-VERIFY66'
$TABLES = @('outsource_material_order','outsource_material_order_item','outsource_delivery','outsource_delivery_item','warehouse_stock','finance_payable')
# NOTE: never name this $base -- $base IS $BASE (case-insensitive) and would clobber the API root URL.
$counts = @{}
foreach ($t in $TABLES) { $counts[$t] = [int](SqlOne "SELECT COUNT(*) FROM $t") }
$baseMaxDelivery = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_delivery")
Write-Output ("baseline: " + (($TABLES | ForEach-Object { $_ + '=' + $counts[$_] }) -join ' ') + " deliveryMaxId=$baseMaxDelivery")

$moid = 0
$stockBefore = 0
$payableBefore = 0
try {
  # ---------- fixture: probe order, ORDERED = 100, RECEIVED = 0 ----------
  Sql "DELETE FROM outsource_delivery WHERE code LIKE 'DEL-VERIFY66%'" | Out-Null
  Sql "DELETE FROM outsource_delivery_item WHERE delivery_id IN (SELECT id FROM outsource_delivery WHERE source_order_id IN (SELECT id FROM outsource_material_order WHERE code='$PROBE'))" | Out-Null
  Sql "DELETE FROM outsource_material_order_item WHERE order_id IN (SELECT id FROM outsource_material_order WHERE code='$PROBE')" | Out-Null
  Sql "DELETE FROM outsource_material_order WHERE code='$PROBE'" | Out-Null
  $cid = SqlOne "SELECT company_id FROM outsource_material_order WHERE id=16"
  if ($cid -eq '') { $cid = '1' }
  Sql "INSERT INTO outsource_material_order(code,supplier_id,order_type,status,company_id,deleted,create_time,update_time) VALUES('$PROBE',34,'PURCHASE','RECEIVING',$cid,0,NOW(),NOW())" | Out-Null
  $moid = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='$PROBE'")
  if ($moid -le 0) { Write-Output 'FATAL: probe order not created'; exit 1 }
  Sql "INSERT INTO outsource_material_order_item(order_id,outsource_material_id,unit,order_quantity,received_quantity,defect_returned_qty,unit_price,amount,company_id,deleted,repair_returned_qty) VALUES($moid,33,'PCS',100,0,0,5,500,$cid,0,0)" | Out-Null
  $miid = [int](SqlOne "SELECT id FROM outsource_material_order_item WHERE order_id=$moid")
  Write-Output ("fixture: orderId=$moid itemId=$miid (ordered=100 received=0, warehouse fixture=74)")
  # NOTE: warehouse_stock uses `material_id`; outsource_delivery_item / outsource_material_order_item
  # use `outsource_material_id`. Mixing them up makes the whole statement fail and SqlOne return the
  # mysql error text (which then breaks the [int] cast) -- exactly the trap documented in tools.md #17.
  $stockBefore = [int](SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=74 AND material_id=33")
  $payableBefore = [int](SqlOne "SELECT IFNULL(SUM(amount),0) FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY'")
  Write-Output ''

  # ================= A) over-receipt must be rejected (first line of defence) =================
  Write-Output '=== A) receive() -- qty above remaining must be rejected ==='
  $rA = Api 'Post' "$BASE/outsource/material-order/$moid/receive" @{
    warehouseId = 74; force = $true; items = @(@{ itemId = $miid; quantity = 101 }) }
  if ((CodeOf $rA) -ne '200') { Ok ("over-receipt 101 > ordered 100 rejected: " + (MsgOf $rA)) }
  else { Bad "over-receipt ACCEPTED (returned id=" + [string]$rA.data + ") -- fix not effective" }

  # ================= B) exactly the remaining qty is allowed (positive control) =================
  Write-Output ''
  Write-Output '=== B) receive() -- exactly the remaining qty must pass (positive control) ==='
  $rB = Api 'Post' "$BASE/outsource/material-order/$moid/receive" @{
    warehouseId = 74; force = $true; items = @(@{ itemId = $miid; quantity = 100 }) }
  if ((CodeOf $rB) -eq '200') { Ok ("receipt of exactly 100 accepted (draft id=" + [string]$rB.data + ")") }
  else { Bad ("valid receipt rejected: " + (MsgOf $rB)); throw 'cannot continue without a draft' }
  $draftId = [int]$rB.data
  Ok ("draft persisted: " + (SqlOne "SELECT CONCAT(code,'|',status) FROM outsource_delivery WHERE id=$draftId"))

  # ================= C) in-flight DRAFT must consume the quota =================
  Write-Output ''
  Write-Output '=== C) receive() -- in-flight DRAFT qty must block a second overlapping receipt ==='
  $rC = Api 'Post' "$BASE/outsource/material-order/$moid/receive" @{
    warehouseId = 74; force = $true; items = @(@{ itemId = $miid; quantity = 1 }) }
  if ((CodeOf $rC) -ne '200') {
    if ((MsgOf $rC) -match '5 ' -or (MsgOf $rC) -match '草稿') { Ok ("second receipt blocked by duplicate guard: " + (MsgOf $rC)) }
    else { Ok ("second receipt blocked (remaining consumed by DRAFT): " + (MsgOf $rC)) }
  } else {
    Bad "second overlapping receipt ACCEPTED -- in-flight DRAFT qty is not counted"
  }

  # control: drop the draft, quota must be released again
  Sql "DELETE FROM outsource_delivery_item WHERE delivery_id=$draftId" | Out-Null
  Sql "DELETE FROM outsource_delivery WHERE id=$draftId" | Out-Null
  $rC2 = Api 'Post' "$BASE/outsource/material-order/$moid/receive" @{
    warehouseId = 74; force = $true; items = @(@{ itemId = $miid; quantity = 100 }) }
  if ((CodeOf $rC2) -eq '200') { Ok ("quota released after the draft was removed (new draft id=" + [string]$rC2.data + ")") }
  else { Bad ("quota NOT released after removing the draft: " + (MsgOf $rC2)) }
  $draftId2 = [int]$rC2.data

  # ================= D) second line of defence: audit() re-checks the cap =================
  Write-Output ''
  Write-Output '=== D) audit() -- second line of defence (received already at the cap) ==='
  Sql "UPDATE outsource_material_order_item SET received_quantity=100 WHERE id=$miid" | Out-Null
  $rD = Api 'Put' "$BASE/outsource/material-order/delivery/$draftId2/audit" $null
  $afterD = SqlOne "SELECT CONCAT(status,'|',IFNULL((SELECT SUM(quantity) FROM warehouse_stock WHERE warehouse_id=74 AND material_id=33),0)) FROM outsource_delivery WHERE id=$draftId2"
  $stockAfterD = [int](SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=74 AND material_id=33")
  $payableAfterD = [int](SqlOne "SELECT IFNULL(SUM(amount),0) FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY'")
  if ((CodeOf $rD) -ne '200' -and $stockAfterD -eq $stockBefore -and $payableAfterD -eq $payableBefore) {
    Ok ("audit rejected and rolled back (no stock/payable change): " + (MsgOf $rD))
  } else {
    Bad ("audit did not roll back: code=" + (CodeOf $rD) + " stock " + $stockBefore + "->" + $stockAfterD + " payable " + $payableBefore + "->" + $payableAfterD)
  }
  Sql "UPDATE outsource_material_order_item SET received_quantity=0 WHERE id=$miid" | Out-Null

  # ================= E) F7-67: dangling item_id must fail the whole audit =================
  Write-Output ''
  Write-Output '=== E) F7-67 -- audit() must FAIL when the delivery item points at a missing order row ==='
  Sql "UPDATE outsource_delivery_item SET item_id=99999999 WHERE delivery_id=$draftId2" | Out-Null
  $rE = Api 'Put' "$BASE/outsource/material-order/delivery/$draftId2/audit" $null
  $stockAfterE = [int](SqlOne "SELECT IFNULL(SUM(quantity),0) FROM warehouse_stock WHERE warehouse_id=74 AND material_id=33")
  $payableAfterE = [int](SqlOne "SELECT IFNULL(SUM(amount),0) FROM finance_payable WHERE source_bill_type='OUTSOURCE_MATERIAL_DELIVERY'")
  if ((CodeOf $rE) -ne '200' -and $stockAfterE -eq $stockBefore -and $payableAfterE -eq $payableBefore) {
    Ok ("dangling item row now aborts the audit: " + (MsgOf $rE))
  } else {
    Bad ("dangling item row did not abort: code=" + (CodeOf $rE) + " stock " + $stockBefore + "->" + $stockAfterE + " payable " + $payableBefore + "->" + $payableAfterE)
  }
  Sql "UPDATE outsource_delivery_item SET item_id=$miid WHERE delivery_id=$draftId2" | Out-Null

  # ================= F) global invariant =================
  Write-Output ''
  Write-Output '=== F) invariant: no order item may exceed its ordered quantity ==='
  $over = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order_item WHERE IFNULL(received_quantity,0) > IFNULL(order_quantity,0)")
  if ($over -eq 0) { Ok 'invariant holds: received_quantity > order_quantity rows = 0' }
  else { Bad "invariant violated: $over item row(s) still over-received (data correction pending?)" }
}
catch {
  # An abort means the assertions never ran -- that must NOT be reported as a pass
  # (a script that dies early and still prints "RESULT PASS" is the trap in tools.md #17/#9).
  Write-Output ("  [FAIL] aborted before all assertions ran: " + $_.Exception.Message)
  $script:fails++
}
finally {
  Write-Output ''
  Write-Output '=== cleanup ==='
  if ($moid -gt 0) {
    Sql "DELETE FROM outsource_delivery_item WHERE delivery_id IN (SELECT id FROM outsource_delivery WHERE source_order_id=$moid)" | Out-Null
    Sql "DELETE FROM outsource_delivery WHERE source_order_id=$moid" | Out-Null
    Sql "DELETE FROM outsource_material_order_item WHERE order_id=$moid" | Out-Null
    Sql "DELETE FROM outsource_material_order WHERE id=$moid" | Out-Null
  }
  Sql "DELETE FROM outsource_delivery WHERE code LIKE 'DEL-VERIFY66%'" | Out-Null
  Sql "DELETE FROM outsource_delivery WHERE id > $baseMaxDelivery AND source_order_id IS NULL AND remark LIKE '%$PROBE%'" | Out-Null
  foreach ($t in $TABLES) {
    $now = [int](SqlOne "SELECT COUNT(*) FROM $t")
    if ($now -eq $counts[$t]) { Ok ("row count restored: $t = $now") }
    else { Bad ("row count drifted: $t " + $counts[$t] + " -> $now") }
  }
  $left = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order WHERE code='$PROBE'")
  if ($left -eq 0) { Ok 'probe rows removed' } else { Bad "probe rows left: $left" }
}

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures)' }
else { Write-Output ("RESULT FAIL: " + $script:fails + " assertion(s) failed") }
