# audit-20260919-outsource-material-probe.ps1  (batch 6b -- material order / receiving)
#
# WHAT IS PROBED
#   T1 (this script): `MaterialOrderServiceImpl.findDeliveriesByOrder` builds
#        LambdaQueryWrapper.eq(source_order_id, id).or().like(remark, code)
#      -- an ungrouped OR. MyBatis-Plus appends the tenant condition (company_id = ?) to the same
#      WHERE clause, and SQL binds AND tighter than OR, so the `remark LIKE` branch can escape the
#      tenant filter:  WHERE company_id = ? AND source_order_id = ? OR remark LIKE ?
#      This script plants a DRAFT delivery row owned by company_id = 999999 whose remark carries the
#      probe order code, then reads GET /api/outsource/material-order/{id} and inspects
#      `lastDeliveryTime`. If it comes back as the foreign row's date, the tenant filter was bypassed.
#
#   T2 (already proven from live data, no write needed -- see the report):
#      receiving has NO "received <= ordered" guard, while product delivery does. Live data already
#      shows 8 item rows with received_quantity > order_quantity (1900 units / 18800 CNY) and 0
#      over-planned rows on the product-delivery side.
#
# DISCIPLINE
#   ZERO LEDGER IMPACT: the probe order is inserted by SQL, the probe delivery row stays DRAFT
#   (never audited). Nothing is audited; all probe rows are removed in finally and every touched
#   table is re-counted against the baseline.
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

$PROBE  = 'MO-PROBE6B'
$TABLES = @('outsource_material_order','outsource_material_order_item','outsource_delivery','outsource_delivery_item','warehouse_stock','finance_payable')
# NOTE: never name this $base -- $base IS $BASE (case-insensitive) and would clobber the API root URL.
$counts = @{}
foreach ($t in $TABLES) { $counts[$t] = [int](SqlOne "SELECT COUNT(*) FROM $t") }
Write-Output ("baseline: " + (($TABLES | ForEach-Object { $_ + '=' + $counts[$_] }) -join ' '))

$moid = 0
try {
  # ---------- fixture: probe material order (RECEIVING so it is visible; PURCHASE => no component check) ----------
  Sql "DELETE FROM outsource_delivery WHERE code='DEL-PROBE6B-LEAK'" | Out-Null
  Sql "DELETE FROM outsource_material_order_item WHERE order_id IN (SELECT id FROM outsource_material_order WHERE code='$PROBE')" | Out-Null
  Sql "DELETE FROM outsource_material_order WHERE code='$PROBE'" | Out-Null
  $cid = SqlOne "SELECT company_id FROM outsource_material_order WHERE id=16"
  if ($cid -eq '') { $cid = '1' }
  Sql "INSERT INTO outsource_material_order(code,supplier_id,order_type,status,company_id,deleted,create_time,update_time) VALUES('$PROBE',34,'PURCHASE','RECEIVING',$cid,0,NOW(),NOW())" | Out-Null
  $moid = [int](SqlOne "SELECT id FROM outsource_material_order WHERE code='$PROBE'")
  if ($moid -le 0) { Write-Output 'FATAL: probe order not created'; exit 1 }
  Sql "INSERT INTO outsource_material_order_item(order_id,outsource_material_id,unit,order_quantity,received_quantity,defect_returned_qty,unit_price,amount,company_id,deleted,repair_returned_qty) VALUES($moid,33,'PCS',10,0,0,5,50,$cid,0,0)" | Out-Null
  Write-Output ("fixture: materialOrderId=$moid (RECEIVING, PURCHASE, company=$cid)")
  Write-Output ''

  # ================= T1: ungrouped OR vs tenant filter =================
  Write-Output '=== T1) GET /outsource/material-order/{id} -- does the OR branch escape company_id? ==='

  $r0 = Api 'Get' "$BASE/outsource/material-order/$moid" $null
  $t0 = [string]$r0.data.lastDeliveryTime
  if ((CodeOf $r0) -eq '200' -and ($t0 -eq '' -or $t0 -eq 'null')) {
    Ok 'baseline: no delivery linked yet => lastDeliveryTime is empty (control)'
  } else {
    Info ("baseline unexpected: code=" + (CodeOf $r0) + " lastDeliveryTime=$t0")
  }

  # foreign-tenant row: company_id = 999999, carries the probe order code in remark, source_order_id NULL
  Sql "INSERT INTO outsource_delivery(code,delivery_type,status,remark,company_id,source_order_id,delivery_date,create_time,update_time) VALUES('DEL-PROBE6B-LEAK','TRANSFER','DRAFT','$PROBE',999999,NULL,'2000-01-01','2000-01-01 00:00:00','2000-01-01 00:00:00')" | Out-Null
  $leakId = [int](SqlOne "SELECT id FROM outsource_delivery WHERE code='DEL-PROBE6B-LEAK'")
  Info "planted foreign-tenant delivery id=$leakId (company_id=999999, remark=$PROBE, create_time=2000-01-01)"

  $r1 = Api 'Get' "$BASE/outsource/material-order/$moid" $null
  $t1 = [string]$r1.data.lastDeliveryTime
  if ($t1 -match '^2000-01-01') {
    Bad "TENANT LEAK: detail() reported lastDeliveryTime=$t1 from a company_id=999999 row (OR branch escaped company_id filter)"
  } else {
    Ok ("no leak: lastDeliveryTime='" + $t1 + "' (tenant condition still applied to the LIKE branch)")
  }

  # negative control: same row, remark no longer matches => it must disappear from every branch
  Sql "UPDATE outsource_delivery SET remark='no-match-here' WHERE id=$leakId" | Out-Null
  $r2 = Api 'Get' "$BASE/outsource/material-order/$moid" $null
  $t2 = [string]$r2.data.lastDeliveryTime
  if ($t2 -eq '' -or $t2 -eq 'null') {
    Ok 'control: with remark cleared the row is invisible again (the finding above is caused by the LIKE branch)'
  } else {
    Info ("control: lastDeliveryTime='" + $t2 + "' (unexpected -- investigate before reporting)")
  }

  # also check the list endpoint (deliveries() uses eq(source_order_id) only => expected safe)
  $r3 = Api 'Get' "$BASE/outsource/material-order/$moid/deliveries" $null
  $n3 = 0
  if ($r3.data) { $n3 = @($r3.data).Count }
  if ($n3 -eq 0) { Ok 'control: /{id}/deliveries returns 0 rows for the foreign probe row (that endpoint filters by source_order_id only)' }
  else { Bad "/{id}/deliveries exposed $n3 foreign row(s)" }

  # ================= T2: receiving has no "received <= ordered" cap (live evidence, no write) =================
  Write-Output ''
  Write-Output '=== T2) receiving cap -- counted from live data (no write performed here) ==='
  $over = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order_item WHERE IFNULL(received_quantity,0) > IFNULL(order_quantity,0)")
  $liveOver = [int](SqlOne "SELECT COUNT(*) FROM (SELECT d.order_id,d.product_id,SUM(d.quantity) q,MAX(p.quantity) plan FROM outsource_order_delivery d JOIN outsource_order_product p ON p.id=d.product_id WHERE d.status='AUDITED' AND d.quantity>0 GROUP BY d.order_id,d.product_id HAVING q>plan) x")
  Info "material-order item rows over-received (live): $over"
  Info "product-delivery rows over-planned (live, guarded chain): $liveOver  <- negative control"
  if ($over -gt 0 -and $liveOver -eq 0) {
    Bad "over-receipt has no server-side cap: $over item row(s) exceed order_quantity while the guarded product-delivery chain has none"
  } elseif ($over -eq 0) {
    Ok 'no live over-receipt row found (defect would remain latent -- static only)'
  }
}
catch {
  Write-Output ("  [INFO] aborted: " + $_.Exception.Message)
}
finally {
  Write-Output ''
  Write-Output '=== cleanup ==='
  Sql "DELETE FROM outsource_delivery WHERE code='DEL-PROBE6B-LEAK'" | Out-Null
  if ($moid -gt 0) {
    Sql "DELETE FROM outsource_delivery_item WHERE delivery_id IN (SELECT id FROM outsource_delivery WHERE source_order_id=$moid)" | Out-Null
    Sql "DELETE FROM outsource_delivery WHERE source_order_id=$moid" | Out-Null
    Sql "DELETE FROM outsource_material_order_item WHERE order_id=$moid" | Out-Null
    Sql "DELETE FROM outsource_material_order WHERE id=$moid" | Out-Null
  }
  foreach ($t in $TABLES) {
    $now = [int](SqlOne "SELECT COUNT(*) FROM $t")
    if ($now -eq $counts[$t]) { Ok ("row count restored: $t = $now") }
    else { Bad ("row count drifted: $t " + $counts[$t] + " -> $now") }
  }
  $left = [int](SqlOne "SELECT COUNT(*) FROM outsource_material_order WHERE code='$PROBE'")
  if ($left -eq 0) { Ok 'probe rows removed' } else { Bad "probe rows left: $left" }
}

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 findings)' }
else { Write-Output ("RESULT: " + $script:fails + " finding(s) reproduced") }
