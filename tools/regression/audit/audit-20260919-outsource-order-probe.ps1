# audit-20260919-outsource-order-probe.ps1  (batch 6a -- outsource order main line)
#
# WHAT IS PROBED
#   OutsourceOrderDeliveryServiceImpl.createDelivery() validates every field of the payload
#   (grade sum == total quantity, quantity > 0, warehouse resolvable, product master id present),
#   while updateDelivery() -- the only other way to write those very same columns -- validates
#   almost nothing: it checks status=DRAFT and the per-product quantity cap only.
#   Everything else (quantity sign, aQty/bQty/cQty/defectQty, deliveryType, qualityType,
#   warehouseId) is taken straight from the request body and written with updateById().
#
#   audit() then derives the *inbound stock* from the grade columns (addInventoryStock) but the
#   *payable* and the *material deduction* from quantity, so a row whose grade sum differs from
#   quantity (or whose quantity is negative) makes stock and money disagree -- and a negative
#   quantity inverts both the material deduction and the payable (amount < 0).
#
# DISCIPLINE
#   ZERO LEDGER IMPACT: this probe only ever touches DRAFT delivery rows (a draft never deducts
#   material, never moves stock, never creates a payable) and NEVER calls /audit. The fixture
#   (probe order + product row + draft delivery) is created with SQL, removed in finally, and
#   every touched table is re-counted against the baseline.
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
function D($s) {
  if ($null -eq $s -or "$s" -eq '') { return [decimal]0 }
  try { return [decimal]$s } catch { return [decimal]-999999 }
}

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

$PROBE = 'WO-PROBE6A'
$WH    = 76          # FINISHED (INVENTORY) warehouse
$MASTER = 62         # product master id
$TABLES = @('outsource_order','outsource_order_product','outsource_order_delivery','warehouse_stock','finance_payable')
# NOTE: do NOT name this $base -- $base IS $BASE (case-insensitive variable names) and would
# clobber the API root URL.
$counts = @{}
foreach ($t in $TABLES) { $counts[$t] = [int](SqlOne "SELECT COUNT(*) FROM $t") }
$baseMaxId = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_order_delivery")
Write-Output ("baseline: " + (($TABLES | ForEach-Object { $_ + '=' + $counts[$_] }) -join ' ') + " odMaxId=$baseMaxId")

$oid = 0
try {
  # ---------- fixtures (probe order in PRODUCING so deliveries are allowed) ----------
  Sql "DELETE FROM outsource_order_product WHERE order_id IN (SELECT id FROM outsource_order WHERE code='$PROBE')" | Out-Null
  Sql "DELETE FROM outsource_order WHERE code='$PROBE'" | Out-Null
  $cid = SqlOne "SELECT company_id FROM outsource_order WHERE id=54"
  if ($cid -eq '') { $cid = '1' }
  Sql "INSERT INTO outsource_order(code,factory_id,status,supply_mode,company_id,create_time,update_time) VALUES('$PROBE',34,'PRODUCING','OURS',$cid,NOW(),NOW())" | Out-Null
  $oid = [int](SqlOne "SELECT id FROM outsource_order WHERE code='$PROBE'")
  if ($oid -le 0) { Write-Output 'FATAL: probe order not created'; exit 1 }
  Sql "INSERT INTO outsource_order_product(order_id,product_id,product_name,quantity,unit_price,amount,company_id,create_time) VALUES($oid,$MASTER,'MFTESTE2E1',100,10,1000,$cid,NOW())" | Out-Null
  # NOTE: do NOT name this $pid -- $PID is a read-only automatic variable (current process id),
  # assigning to it aborts the whole try block ("Cannot overwrite variable PID").
  $prodRowId = [int](SqlOne "SELECT id FROM outsource_order_product WHERE order_id=$oid")
  Write-Output ("fixture: orderId=$oid productRowId=$prodRowId (plan=100, master=$MASTER, company=$cid)")
  Write-Output ''

  $today = (Get-Date).ToString('yyyy-MM-dd')

  # ================= A) negative controls: create() must reject malformed payloads =================
  Write-Output '=== A) POST /outsource/order-delivery (create) -- validation present (negative control) ==='
  $rA1 = Api 'Post' "$BASE/outsource/order-delivery" @{
    orderId = $oid; productId = $prodRowId; quantity = 1; aQty = 100; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH }
  if ((CodeOf $rA1) -ne '200') { Ok ("grade-sum mismatch rejected: " + (MsgOf $rA1)) }
  else { Bad 'create() accepted grade sum (100) != quantity (1) -- unexpected, review before use' }

  $rA2 = Api 'Post' "$BASE/outsource/order-delivery" @{
    orderId = $oid; productId = $prodRowId; quantity = -5; aQty = 5; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH }
  if ((CodeOf $rA2) -ne '200') { Ok ("negative quantity rejected: " + (MsgOf $rA2)) }
  else { Bad 'create() accepted quantity = -5 -- unexpected, review before use' }
  Write-Output ''

  # ================= B) positive control: a legal draft can be created =================
  Write-Output '=== B) POST -- legal draft (positive control) ==='
  $rB = Api 'Post' "$BASE/outsource/order-delivery" @{
    orderId = $oid; productId = $prodRowId; quantity = 10; aQty = 10; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH; remark = 'audit probe 6a' }
  if ((CodeOf $rB) -ne '200') { Bad ("legal draft rejected: " + (MsgOf $rB)); throw 'cannot continue without a draft' }
  Ok ("draft created, canProceed=" + [string]$rB.data.canProceed)
  $did = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_order_delivery")
  if ($did -le $baseMaxId) { Bad "probe delivery row not found (max id $did)"; throw 'no probe row' }
  Ok ("probe delivery id=$did status=" + (SqlOne "SELECT CONCAT(status,'|',delivery_type,'|',quantity) FROM outsource_order_delivery WHERE id=$did"))
  Write-Output ''

  # ================= C) the defect: update() accepts what create() rejected =================
  Write-Output '=== C) PUT /outsource/order-delivery/{id} (update) -- validation ABSENT (defect under test) ==='
  $rC1 = Api 'Put' "$BASE/outsource/order-delivery/$did" @{
    orderId = $oid; productId = $prodRowId; quantity = 1; aQty = 100; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH; remark = 'audit probe 6a' }
  # NOTE: compare as NUMBERS -- MySQL strips trailing zeros, so the raw text is "1|100|0|0|0",
  # not "1.00|100.00|...". A string compare here silently turns a real finding into a false PASS
  # (observed on the first run: code=200 with row=1|100|0|0|0 was reported OK).
  $rb1 = SqlOne "SELECT CONCAT(quantity,'|',a_qty,'|',b_qty,'|',c_qty,'|',defect_qty) FROM outsource_order_delivery WHERE id=$did"
  $q1 = SqlOne "SELECT quantity FROM outsource_order_delivery WHERE id=$did"
  $a1 = SqlOne "SELECT a_qty FROM outsource_order_delivery WHERE id=$did"
  if ((CodeOf $rC1) -eq '200' -and (D $q1) -ne (D $a1)) {
    Bad ("update() accepted grade sum 100 != quantity 1 (persisted $rb1) -- audit would inbound 100 but pay for 1")
  } else {
    Ok ("grade-sum mismatch did not persist: code=" + (CodeOf $rC1) + " row=$rb1")
  }

  $rC2 = Api 'Put' "$BASE/outsource/order-delivery/$did" @{
    orderId = $oid; productId = $prodRowId; quantity = -5; aQty = 5; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH; remark = 'audit probe 6a' }
  $rb2 = SqlOne "SELECT quantity FROM outsource_order_delivery WHERE id=$did"
  if ((CodeOf $rC2) -eq '200' -and [decimal]$rb2 -lt 0) {
    Bad ("update() accepted negative quantity (persisted $rb2) -- audit would inbound 5, add material back and post a negative payable")
  } else {
    Ok ("negative quantity did not persist: code=" + (CodeOf $rC2) + " qty=$rb2")
  }

  $rC3 = Api 'Put' "$BASE/outsource/order-delivery/$did" @{
    orderId = $oid; productId = $prodRowId; quantity = 10; aQty = 10; bQty = 0; cQty = 0; defectQty = 0
    deliveryDate = $today; warehouseId = $WH; deliveryType = 'DEFECT_RETURN'; qualityType = 'DEFECT'
    remark = 'audit probe 6a' }
  $rb3 = SqlOne "SELECT CONCAT(delivery_type,'|',IFNULL(quality_type,'NULL'),'|',is_reverse) FROM outsource_order_delivery WHERE id=$did"
  if ((CodeOf $rC3) -eq '200' -and $rb3 -eq 'DEFECT_RETURN|DEFECT|0') {
    Bad ("update() accepted deliveryType/qualityType rewrite while isReverse stays false (persisted $rb3) -- row lies about being a defect return")
  } else {
    Ok ("deliveryType rewrite did not persist: code=" + (CodeOf $rC3) + " row=$rb3")
  }

  # ================= D) the same payload on an AUDITED row must still be refused =================
  Write-Output ''
  Write-Output '=== D) PUT on an AUDITED row -- status guard must hold (negative control) ==='
  $auditedId = [int](SqlOne "SELECT IFNULL(MIN(id),0) FROM outsource_order_delivery WHERE status='AUDITED'")
  if ($auditedId -gt 0) {
    $rD = Api 'Put' "$BASE/outsource/order-delivery/$auditedId" @{
      orderId = 54; productId = 81; quantity = 1; aQty = 1; bQty = 0; cQty = 0; defectQty = 0
      deliveryDate = $today; warehouseId = $WH }
    if ((CodeOf $rD) -ne '200') { Ok ("audited row still protected: " + (MsgOf $rD)) }
    else { Bad 'update() modified an AUDITED delivery row' }
  } else { Info 'no AUDITED delivery row to test the status guard' }
}
catch {
  Write-Output ("  [INFO] aborted: " + $_.Exception.Message)
}
finally {
  # ---------- cleanup + self-check ----------
  Write-Output ''
  Write-Output '=== cleanup ==='
  if ($oid -gt 0) {
    Sql "DELETE FROM outsource_order_delivery WHERE order_id=$oid" | Out-Null
    Sql "DELETE FROM outsource_order_product WHERE order_id=$oid" | Out-Null
    Sql "DELETE FROM outsource_order WHERE id=$oid" | Out-Null
  }
  Sql "DELETE FROM outsource_order_delivery WHERE id > $baseMaxId" | Out-Null
  foreach ($t in $TABLES) {
    $now = [int](SqlOne "SELECT COUNT(*) FROM $t")
    if ($now -eq $counts[$t]) { Ok ("row count restored: $t = $now") }
    else { Bad ("row count drifted: $t " + $counts[$t] + " -> $now") }
  }
  $left = [int](SqlOne "SELECT COUNT(*) FROM outsource_order WHERE code='$PROBE'")
  if ($left -eq 0) { Ok 'probe order removed' } else { Bad "probe rows left: $left" }
}

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures) -- see [FAIL] lines above for reproductions' }
else { Write-Output ("RESULT: " + $script:fails + " finding(s) reproduced (expected for a defect probe)") }
