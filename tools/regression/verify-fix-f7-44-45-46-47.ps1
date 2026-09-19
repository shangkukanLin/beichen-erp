# verify-fix-f7-44-45-46-47.ps1  (regression for F7-44 / F7-45 / F7-46 / F7-47)
#
# F7-44: the GENERIC delivery unaudit did not dispatch by type, so un-auditing a RECEIVE /
#        DEFECT_RETURN document from the "物料收发单" page reversed stock + payable only --
#        it did NOT roll back the order's received/defect quantities, did not restore BOM
#        components and did not reverse material cost (the specific entry did). Fix = dispatch.
# F7-45: delivery unaudit / unauditMaterialDelivery had no CAS (the forward paths did) -> two
#        concurrent un-audits could both pass the status check and reverse twice.
# F7-46: outsource_material_order audit / unAudit / cancel / finish had no CAS at all.
# F7-47: sale_return had NO index on code and outsource_delivery only a NON-UNIQUE one, while every
#        other document table has uk_code -> concurrent "max+1" numbering could duplicate silently.
#
# The CAS fixes are proved with PARALLEL probes (6 concurrent calls, exactly one must win);
# F7-44 is proved by a full un-audit -> re-audit round trip that is asserted against the baseline.
# Everything is captured beforehand and restored afterwards (see the final self-check).
# ASCII-only on purpose.

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$PAR   = 6          # concurrent calls per probe

$env:MYSQL_PWD = 'root'
$script:fails = 0
function Sql([string]$sql) {
  $out = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>&1
  return ($out | Out-String).Trim()
}
function SqlOne([string]$sql) { $v = Sql $sql; if ($v -eq '') { return '' }; return ($v -split "`n")[0].Trim() }
function SqlRows([string]$sql) { $v = Sql $sql; if ($v -eq '') { return @() }; return @($v -split "`n") }
function Ok([string]$m)   { Write-Output ("  [OK]   " + $m) }
function Bad([string]$m)  { Write-Output ("  [FAIL] " + $m); $script:fails++ }
function Info([string]$m) { Write-Output ("  [INFO] " + $m) }
function D($s) { if ($null -eq $s -or "$s" -eq '') { return [decimal]0 }; return [decimal]$s }
function UniqueIdx([string]$t) {
  return (SqlOne "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='beichen_erp' AND TABLE_NAME='$t' AND COLUMN_NAME='code' AND NON_UNIQUE=0")
}
function Dups([string]$t) { return (SqlOne "SELECT COUNT(*) FROM (SELECT code FROM $t GROUP BY code HAVING COUNT(*)>1) x") }

$body = '{"username":"lin","password":"123","companyId":1}'
try {
  $login = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($body))
} catch { Write-Output ("LOGIN EX " + $_.Exception.Message); exit 1 }
$TOKEN = [string]$login.data.token
$H = @{ Authorization = $TOKEN }
function Api([string]$method, [string]$url) {
  try { return Invoke-RestMethod -Uri $url -Method $method -Headers $H }
  catch {
    $code = -1
    try { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } } catch { }
    return [pscustomobject]@{ code = $code; msg = "HTTPEX " + $_.Exception.Message }
  }
}
function JCode($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function JMsg($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }
# fire N concurrent PUTs, return the array of responses  (note: `Parallel` is a RESERVED word in PS)
function FireParallel($url, $n) {
  $jobs = @()
  foreach ($i in 1..$n) {
    $jobs += Start-Job -ScriptBlock {
      param($u, $t)
      try { return Invoke-RestMethod -Uri $u -Method Put -Headers @{ Authorization = $t } }
      catch {
        $c = -1; try { if ($_.Exception.Response) { $c = [int]$_.Exception.Response.StatusCode } } catch { }
        return [pscustomobject]@{ code = $c; msg = "HTTPEX" }
      }
    } -ArgumentList $url, $TOKEN
  }
  $res = @()
  foreach ($j in $jobs) { $res += (Receive-Job -Job $j -Wait) }
  Remove-Job -Job $jobs -Force
  return $res
}
function SuccessCount($res) { $n = 0; foreach ($r in $res) { if ((JCode $r) -eq '200') { $n++ } }; return $n }

# ---------------- fixtures & baseline ----------------
Write-Output '=== 0) fixtures & baseline ==='
$DELIV = [int](SqlOne "SELECT d.id FROM outsource_delivery d JOIN outsource_delivery_item i ON i.delivery_id=d.id WHERE d.delivery_type='RECEIVE' AND d.status='AUDITED' AND d.source_order_id IS NOT NULL GROUP BY d.id ORDER BY d.id DESC LIMIT 1")
$MO    = [int](SqlOne "SELECT id FROM outsource_material_order WHERE status='RECEIVING' ORDER BY id DESC LIMIT 1")
$dWh   = SqlOne "SELECT to_warehouse_id FROM outsource_delivery WHERE id=$DELIV"
$dOrder = SqlOne "SELECT source_order_id FROM outsource_delivery WHERE id=$DELIV"
$dStatus0 = SqlOne "SELECT status FROM outsource_delivery WHERE id=$DELIV"
$moStatus0 = SqlOne "SELECT status FROM outsource_material_order WHERE id=$MO"
$oi0 = SqlRows "SELECT id,IFNULL(received_quantity,0),IFNULL(defect_returned_qty,0) FROM outsource_material_order_item WHERE order_id=$dOrder ORDER BY id"
$pay0 = SqlRows "SELECT id,status FROM finance_payable WHERE source_id=$DELIV AND source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' ORDER BY id"
$maxPay = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_payable')
$maxLog = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
$cDeliv = SqlOne 'SELECT COUNT(*) FROM outsource_delivery'
$cPay = SqlOne 'SELECT COUNT(*) FROM finance_payable'
# per-material: delivery quantity + warehouse stock BEFORE the round trip
$itemQty = @{}
foreach ($r in (SqlRows "SELECT i.outsource_material_id, SUM(i.quantity) FROM outsource_delivery_item i WHERE i.delivery_id=$DELIV AND i.outsource_material_id IS NOT NULL GROUP BY i.outsource_material_id")) {
  $p = $r -split "`t"
  if ($p.Count -ge 2) { $itemQty[[int]$p[0]] = D $p[1] }
}
$whQty0 = @{}
foreach ($k in $itemQty.Keys) { $whQty0[$k] = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$dWh AND material_id=$k") }
Info "delivery=$DELIV (order=$dOrder, wh=$dWh, status=$dStatus0) ; material order=$MO (status=$moStatus0)"
Info "order items=$($oi0.Count) ; payables=$($pay0.Count) ; materials=$($itemQty.Count)"
if ($DELIV -le 0 -or $MO -le 0) { Bad 'fixtures missing -> abort'; exit 1 }

# ---------------- F7-47 ----------------
Write-Output '=== 1) F7-47 单号唯一键 ==='
foreach ($t in @('sale_return','outsource_delivery','outsource_material_order')) {
  $u = UniqueIdx $t
  if ((D $u) -ge 1) { Ok "$t has a UNIQUE index on code" } else { Bad "$t has NO unique index on code" }
  $d = Dups $t
  if ((D $d) -eq 0) { Ok "$t has no duplicate code (safe to enforce)" } else { Bad "$t has $d duplicated code(s)" }
}

# ---------------- F7-45 (parallel CAS on generic un-audit) ----------------
Write-Output "=== 2) F7-45 并发反审核：$PAR 个请求只能有一个成功 ==="
$res = FireParallel "$BASE/outsource/delivery/$DELIV/un-audit" $PAR
$okN = SuccessCount $res
if ($okN -eq 1) { Ok "exactly 1 of $PAR concurrent un-audits succeeded (CAS works)" }
else { Bad "$okN of $PAR concurrent un-audits succeeded (expect exactly 1)" }
$msgs = @(); foreach ($r in $res) { if ((JCode $r) -ne '200') { $msgs += (JMsg $r) } }
if ($msgs.Count -eq ($PAR - 1)) { Ok "the other $($msgs.Count) failed" } else { Bad "unexpected failure count: $($msgs.Count)" }
$dStatus1 = SqlOne "SELECT status FROM outsource_delivery WHERE id=$DELIV"
if ($dStatus1 -eq 'DRAFT') { Ok "delivery is DRAFT after un-audit" } else { Bad "delivery status=$dStatus1 (expect DRAFT)" }

# ---------------- F7-44 (dispatch => full rollback) ----------------
Write-Output '=== 3) F7-44 通用反审核必须做完整回滚（分流到专用方法） ==='
$oi1 = SqlRows "SELECT id,IFNULL(received_quantity,0),IFNULL(defect_returned_qty,0) FROM outsource_material_order_item WHERE order_id=$dOrder ORDER BY id"
$rolledBack = $true
foreach ($row in $oi1) {
  $p = $row -split "`t"
  $b = $oi0 | Where-Object { ($_ -split "`t")[0] -eq $p[0] } | Select-Object -First 1
  if ($b) {
    $bp = $b -split "`t"
    if ((D $p[1]) -ne (D $bp[1]) -or (D $p[2]) -ne (D $bp[2])) { $rolledBack = $false }
  }
}
if ($rolledBack) { Bad 'order quantities were NOT rolled back (dispatch missing)' }
else { Ok 'order received/defect quantities WERE rolled back (generic entry dispatches to the material path)' }
$payCancelled = SqlOne "SELECT COUNT(*) FROM finance_payable WHERE source_id=$DELIV AND source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' AND status='CANCELLED'"
if ((D $payCancelled) -ge 1) { Ok 'the payable of this delivery was reversed' } else { Bad 'payable was not reversed' }
$stockBad = @()
foreach ($k in $itemQty.Keys) {
  $expect = $whQty0[$k] - $itemQty[$k]
  $now = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$dWh AND material_id=$k")
  Info "material ${k}: baseline=$($whQty0[$k]) - delivery=$($itemQty[$k]) -> now=$now"
  if ($now -ne $expect) { $stockBad += ("m$k now=$now expect=$expect") }
}
if ($itemQty.Count -eq 0) { Info 'no material stock to check (delivery items without material_id)' }
elseif ($stockBad.Count -eq 0) { Ok "stock reversed by exactly the delivery quantity ($($itemQty.Count) material(s))" }
else { Bad ('stock reversal wrong: ' + ($stockBad -join ' ; ')) }

# ---------------- restore via the specific audit ----------------
Write-Output '=== 4) 还原：走专用审核入口 ==='
$ra = Api 'PUT' "$BASE/outsource/material-order/delivery/$DELIV/audit"
if ((JCode $ra) -eq '200') { Ok 're-audit via the specific entry ok' } else { Bad ("re-audit failed: " + (JMsg $ra)) }
$dStatus2 = SqlOne "SELECT status FROM outsource_delivery WHERE id=$DELIV"
if ($dStatus2 -eq $dStatus0) { Ok "delivery status restored ($dStatus2)" } else { Bad "delivery status=$dStatus2 (expect $dStatus0)" }
$oi2 = SqlRows "SELECT id,IFNULL(received_quantity,0),IFNULL(defect_returned_qty,0) FROM outsource_material_order_item WHERE order_id=$dOrder ORDER BY id"
$oiSame = $true
foreach ($row in $oi2) {
  $p = $row -split "`t"
  $b = $oi0 | Where-Object { ($_ -split "`t")[0] -eq $p[0] } | Select-Object -First 1
  if ($b) { $bp = $b -split "`t"; if ((D $p[1]) -ne (D $bp[1]) -or (D $p[2]) -ne (D $bp[2])) { $oiSame = $false } }
}
if ($oiSame) { Ok 'order quantities restored to baseline' } else { Bad 'order quantities differ from baseline' }
$stockBad2 = @()
foreach ($k in $itemQty.Keys) {
  $now = D (SqlOne "SELECT IFNULL(quantity,0) FROM warehouse_stock WHERE warehouse_id=$dWh AND material_id=$k")
  if ($now -ne $whQty0[$k]) { $stockBad2 += ("m$k now=$now expect=$($whQty0[$k])") }
}
if ($itemQty.Count -eq 0) { Info 'no material stock to restore' }
elseif ($stockBad2.Count -eq 0) { Ok 'stock restored to baseline' } else { Bad ('stock not restored: ' + ($stockBad2 -join ' ; ')) }
# payable: drop rows created by the re-audit, restore the original rows' status
Sql "DELETE FROM finance_payable WHERE id > $maxPay AND source_id=$DELIV AND source_bill_type='OUTSOURCE_MATERIAL_DELIVERY'" | Out-Null
foreach ($row in $pay0) {
  $p = $row -split "`t"
  Sql "UPDATE finance_payable SET status='$($p[1])' WHERE id=$($p[0])" | Out-Null
}
$payNow = (SqlRows "SELECT id,status FROM finance_payable WHERE source_id=$DELIV AND source_bill_type='OUTSOURCE_MATERIAL_DELIVERY' ORDER BY id") -join ','
$payBase = ($pay0 -join ',')
if ($payNow -eq $payBase) { Ok "payable rows restored ($payNow)" } else { Bad "payable rows: $payNow vs $payBase" }

# ---------------- F7-46 (parallel CAS on material order) ----------------
# NOTE: every RECEIVING material order in this DB already has deliveries, so un-audit / cancel are
# blocked by the业务护栏 ("该订单已有交货记录"). `finish` (RECEIVING -> FINISHED) has no side effects
# and can be restored with a targeted SQL update, so it is used as the CAS discriminator: before the
# fix all N calls passed the non-atomic status check, after the fix exactly one must win.
Write-Output "=== 5) F7-46 物料订单并发流转（finish）：$PAR 个请求只能有一个成功 ==="
$moFinish0 = SqlOne "SELECT IFNULL(finish_time,'') FROM outsource_material_order WHERE id=$MO"
$res2 = FireParallel "$BASE/outsource/material-order/$MO/finish" $PAR
$okN2 = SuccessCount $res2
if ($okN2 -eq 1) { Ok "exactly 1 of $PAR concurrent finish calls succeeded (CAS works)" }
else { Bad "$okN2 of $PAR concurrent finish calls succeeded (expect exactly 1)" }
$moNow = SqlOne "SELECT status FROM outsource_material_order WHERE id=$MO"
if ($moNow -eq 'FINISHED') { Ok "material order finished exactly once" } else { Bad "material order status=$moNow (expect FINISHED)" }
# restore (status-only fixture, restored with an explicit targeted update)
if ($moFinish0 -eq '') { Sql "UPDATE outsource_material_order SET status='$moStatus0', finish_time=NULL WHERE id=$MO" | Out-Null }
else { Sql "UPDATE outsource_material_order SET status='$moStatus0', finish_time='$moFinish0' WHERE id=$MO" | Out-Null }
$moBack = SqlOne "SELECT status FROM outsource_material_order WHERE id=$MO"
if ($moBack -eq $moStatus0) { Ok "material order restored ($moBack)" } else { Bad "material order restore failed: $moBack (expect $moStatus0)" }

# ---------------- self check ----------------
Write-Output '=== 9) 收尾自检 ==='
$p2 = SqlOne "SELECT COUNT(*) FROM finance_payable WHERE id > $maxPay"
$d2 = SqlOne 'SELECT COUNT(*) FROM outsource_delivery'
if ((D $p2) -eq 0) { Ok 'no leftover payable row' } else { Bad "$p2 leftover payable row(s)" }
if ($d2 -eq $cDeliv) { Ok "outsource_delivery row count unchanged ($d2)" } else { Bad "outsource_delivery count $cDeliv -> $d2" }
$logs = SqlOne "SELECT COUNT(*) FROM warehouse_stock_log WHERE id > $maxLog"
Info "new stock log rows created by the round trip: $logs (expected, kept as ledger trail)"

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
