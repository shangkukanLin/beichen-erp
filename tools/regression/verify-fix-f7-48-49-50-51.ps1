# verify-fix-f7-48-49-50-51.ps1  (regression for the batch-9 P3 fixes)
#
# F7-48: un-audit must CLEAR the audit fields. updateById ignores null fields, so SaleOrder/SaleExchange
#        used to keep audit_time (and auditor) after un-audit -- 2 sale_order DRAFT rows carried it.
# F7-49: order-item accumulations must be atomic SQL. Before the fix they were Java read-modify-write,
#        so two documents touching the same material_order_item row could overwrite each other.
# F7-50: cancel must be a CAS (claim DRAFT->CANCELLED). Before the fix the "select then update" pattern let
#        cancel overwrite a document that audit had just turned AUDITED (stock already applied, no reversal).
# F7-51: ReturnSort exposes canonical unAudit(); cancel() is a deprecated delegate (same behaviour).
#
# Assertions are paired with positive controls and probe rows are deleted at the end (row counts re-checked).
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

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
function Rejected($r) { return ((CodeOf $r) -ne '200') }

$body = '{"username":"lin","password":"123","companyId":1}'
try {
  $login = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($body))
} catch { Write-Output ("LOGIN EX " + $_.Exception.Message); exit 1 }
$H = @{ Authorization = [string]$login.data.token }
$TOKEN = [string]$login.data.token
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

# ---------- baseline ----------
$TABLES = @('sale_order','sale_exchange','inventory_stock_take','inventory_warehouse_move','inventory_other_io',
            'inventory_stock_loss','inventory_stock_reclass','outsource_delivery','outsource_material_return',
            'outsource_return_order','outsource_stock_loss','outsource_other_io','outsource_material_order_item',
            'warehouse_stock','return_sort','after_sale_pending','finance_receivable','finance_cashflow')
# NOTE: do NOT name this $base -- PowerShell variable names are case-insensitive, so $base IS $BASE and
# would clobber the API root URL (observed: "remote name could not be resolved: system.collections.hashtable").
$counts = @{}
foreach ($t in $TABLES) { $counts[$t] = [int](SqlOne "SELECT COUNT(*) FROM $t") }

# ================= F7-48 =================
Write-Output '=== F7-48: un-audit must clear the audit fields ==='

# A1: global invariant -- no DRAFT document may carry audit_time in any of the 11 audit-field tables
$auditTables = @('finance_payable_transfer','inventory_stock_loss','inventory_stock_take','outsource_material_return',
                 'outsource_return_order','outsource_stock_loss','purchase_exchange','purchase_order','purchase_return',
                 'sale_exchange','sale_order','sale_return')
$dirty = 0
foreach ($t in $auditTables) {
  $c = [int](SqlOne "SELECT COUNT(*) FROM $t WHERE status='DRAFT' AND audit_time IS NOT NULL")
  if ($c -gt 0) { Info ("$t : $c DRAFT row(s) with audit_time") }
  $dirty += $c
}
if ($dirty -eq 0) { Ok ("invariant holds: 0 DRAFT doc carries audit_time across " + $auditTables.Count + " tables") }
else { Bad ("$dirty DRAFT doc(s) still carry audit_time") }

# A2: round trip on a real AUDITED sale order (un-audit -> audit_time must be NULL, audit -> restored).
# NOTE: AUDITED orders get locked by business rules once they carry receipts (observed:
# "应收单「XS-...」已有收款记录，不可反审核"), so walk the candidates and use the first one the
# service actually accepts -- same approach as the sale_exchange branch below.
$soIds = @()
$soRaw = Sql "SELECT id FROM sale_order WHERE status='AUDITED' AND IFNULL(settle_type,'')<>'CASH' ORDER BY id DESC"
foreach ($line in ($soRaw -split "`n")) {
  $t = "$line".Trim()
  if ($t -match '^\d+$') { $soIds += [int]$t }
}
$soTested = $false
foreach ($sid in $soIds) {
  $soBaseAudit = SqlOne "SELECT IFNULL(audit_time,'NULL') FROM sale_order WHERE id=$sid"
  $r = Api 'Put' "$BASE/inventory/sale/$sid/un-audit" $null
  if ((CodeOf $r) -ne '200') { Info ("sale_order $sid un-audit refused: " + (MsgOf $r)); continue }
  $afterUn = SqlOne "SELECT CONCAT(status,'|',IFNULL(audit_time,'NULL')) FROM sale_order WHERE id=$sid"
  if ($afterUn -eq 'DRAFT|NULL') { Ok ("sale_order $sid un-audit cleared audit_time: $afterUn (was $soBaseAudit)") }
  else { Bad ("sale_order $sid un-audit left audit fields: $afterUn") }
  $r2 = Api 'Put' "$BASE/inventory/sale/$sid/audit" $null
  $afterRe = SqlOne "SELECT CONCAT(status,'|',IFNULL(audit_time,'NULL')) FROM sale_order WHERE id=$sid"
  if ((CodeOf $r2) -eq '200' -and $afterRe -ne 'DRAFT|NULL' -and $afterRe -ne 'AUDITED|NULL') {
    Ok ("sale_order $sid re-audit restored the doc: $afterRe")
  } else {
    Bad ("sale_order $sid re-audit problem: code=" + (CodeOf $r2) + " msg=" + (MsgOf $r2) + " row=$afterRe")
  }
  $soTested = $true
  break
}
if (-not $soTested) { Info 'no AUDITED sale_order could be un-audited (all locked by business rules) -- SO branch verified by code review only' }

# A3: same for sale_exchange when an un-auditable fixture exists (auditor_id/name are cleared there too).
# Several AUDITED exchanges are locked by business rules (goods already sorted, etc.), so walk the
# candidates and use the first one the service accepts.
$seIds = @()
$seRaw = Sql "SELECT id FROM sale_exchange WHERE status='AUDITED' ORDER BY id"
foreach ($line in ($seRaw -split "`n")) {
  $t = "$line".Trim()
  if ($t -match '^\d+$') { $seIds += [int]$t }
}
$seTested = $false
foreach ($sid in $seIds) {
  $r = Api 'Put' "$BASE/sale/exchange/$sid/un-audit" $null
  if ((CodeOf $r) -ne '200') { Info ("sale_exchange $sid un-audit refused: " + (MsgOf $r)); continue }
  $seRow = SqlOne "SELECT CONCAT(status,'|',IFNULL(audit_time,'NULL'),'|',IFNULL(auditor_id,'NULL'),'|',IFNULL(auditor_name,'NULL')) FROM sale_exchange WHERE id=$sid"
  if ($seRow -eq 'DRAFT|NULL|NULL|NULL') { Ok ("sale_exchange $sid un-audit cleared status|audit_time|auditor: $seRow") }
  else { Bad ("sale_exchange $sid un-audit left audit fields: $seRow") }
  $rb = Api 'Put' "$BASE/sale/exchange/$sid/audit" $null
  $seBack = SqlOne "SELECT CONCAT(status,'|',IFNULL(audit_time,'NULL')) FROM sale_exchange WHERE id=$sid"
  if ((CodeOf $rb) -eq '200' -and $seBack -ne 'DRAFT|NULL' -and $seBack -ne 'AUDITED|NULL') {
    Ok ("sale_exchange re-audit restored: $seBack")
  } else {
    Bad ("sale_exchange re-audit problem: code=" + (CodeOf $rb) + " row=$seBack")
  }
  $seTested = $true
  break
}
if (-not $seTested) { Info 'no AUDITED sale_exchange could be un-audited (all locked by business rules) -- SE branch verified by code review only' }

# ================= F7-49 =================
Write-Output '=== F7-49: atomic SQL accumulation (equivalence + clamp + round trip) ==='

# B1: the exact SQL expressions used by the fix must behave like the old safeAdd/safeSubtract
$probeOi = [int](SqlOne "SELECT IFNULL(MIN(id),0) FROM outsource_material_order_item")
$oiBase = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
Sql "UPDATE outsource_material_order_item SET received_quantity = IFNULL(received_quantity,0) + (5) WHERE id=$probeOi" | Out-Null
$p1 = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
if ((D $p1) -eq ((D $oiBase) + 5)) { Ok ("atomic add: $oiBase -> $p1") } else { Bad "atomic add wrong: $oiBase -> $p1 (expect +5)" }
Sql "UPDATE outsource_material_order_item SET received_quantity = GREATEST(IFNULL(received_quantity,0) - (5), 0) WHERE id=$probeOi" | Out-Null
$p2 = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
if ((D $p2) -eq (D $oiBase)) { Ok ("atomic subtract restored: $p2") } else { Bad "atomic subtract wrong: $p2 (expect $oiBase)" }
Sql "UPDATE outsource_material_order_item SET received_quantity = 0 WHERE id=$probeOi" | Out-Null
Sql "UPDATE outsource_material_order_item SET received_quantity = GREATEST(IFNULL(received_quantity,0) - (5), 0) WHERE id=$probeOi" | Out-Null
$p3 = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
if ((D $p3) -eq 0) { Ok 'GREATEST clamp: 0 - 5 stays 0 (never negative)' } else { Bad "clamp failed: $p3" }
Sql "UPDATE outsource_material_order_item SET received_quantity = $oiBase WHERE id=$probeOi" | Out-Null
$p4 = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
if ((D $p4) -eq (D $oiBase)) { Ok "probe row restored to $p4" } else { Bad "probe row NOT restored: $p4 vs $oiBase" }

# B2: round trip on a real AUDITED receive delivery (order item must fall back and come back exactly)
$dlv = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM outsource_delivery WHERE status='AUDITED' AND delivery_type='RECEIVE'")
if ($dlv -gt 0) {
  $itRow = SqlOne "SELECT CONCAT(IFNULL(item_id,0),'|',IFNULL(quantity,0)) FROM outsource_delivery_item WHERE delivery_id=$dlv AND item_id IS NOT NULL ORDER BY id LIMIT 1"
  $ip = $itRow -split '\|'
  $dlvItem = [int]$ip[0]
  $dlvQty = D $ip[1]
  $oiBefore = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$dlvItem"
  $wsBefore = SqlOne "SELECT IFNULL(SUM(IFNULL(quantity,0)),0) FROM warehouse_stock"
  Info ("fixture outsource_delivery id=$dlv -> material_order_item $dlvItem (received=$oiBefore, qty=$dlvQty)")
  $r = Api 'Put' "$BASE/outsource/delivery/$dlv/un-audit" $null
  $oiAfter = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$dlvItem"
  $dlvSt = SqlOne "SELECT status FROM outsource_delivery WHERE id=$dlv"
  if ((CodeOf $r) -eq '200' -and $dlvSt -eq 'DRAFT') {
    if ((D $oiAfter) -eq ((D $oiBefore) - $dlvQty)) {
      Ok ("un-audit rolled the order item back: $oiBefore -> $oiAfter (-$dlvQty)")
    } else {
      Bad ("order item rollback wrong: $oiBefore -> $oiAfter (expect -$dlvQty)")
    }
    if ((D $oiAfter) -ge 0) { Ok "rolled back quantity is not negative" } else { Bad "rolled back quantity went negative: $oiAfter" }
    $ra = Api 'Put' "$BASE/outsource/delivery/$dlv/audit" $null
    $oiBack = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$dlvItem"
    $wsBack = SqlOne "SELECT IFNULL(SUM(IFNULL(quantity,0)),0) FROM warehouse_stock"
    $dlvSt2 = SqlOne "SELECT status FROM outsource_delivery WHERE id=$dlv"
    if ((CodeOf $ra) -eq '200' -and (D $oiBack) -eq (D $oiBefore) -and $dlvSt2 -eq 'AUDITED') {
      Ok ("re-audit restored the order item ($oiBack) and doc status $dlvSt2")
    } else {
      Bad ("re-audit problem: code=" + (CodeOf $ra) + " item=$oiBack (expect $oiBefore) status=$dlvSt2")
    }
    if ((D $wsBack) -eq (D $wsBefore)) { Ok ("total warehouse stock restored ($wsBack)") }
    else { Bad ("warehouse stock drifted: $wsBefore -> $wsBack") }
  } else {
    Bad ("un-audit failed: code=" + (CodeOf $r) + " msg=" + (MsgOf $r) + " status=$dlvSt")
  }
} else { Info 'no AUDITED receive delivery fixture; skipped' }

# ================= F7-50 =================
Write-Output '=== F7-50: cancel is a CAS (concurrency probe + no-overwrite + sampled no-regression) ==='
$stamp = Get-Date -Format 'HHmmss'
$pA = "PD-V50A-$stamp"
$pB = "PD-V50B-$stamp"
Sql "INSERT INTO inventory_stock_take (take_no,warehouse_id,warehouse_name,period,take_date,status,company_id) VALUES ('$pA',68,'V50 PROBE A','2026-09','2026-09-19','DRAFT',1)" | Out-Null
Sql "INSERT INTO inventory_stock_take (take_no,warehouse_id,warehouse_name,period,take_date,status,company_id) VALUES ('$pB',68,'V50 PROBE B','2026-09','2026-09-19','AUDITED',1)" | Out-Null
$idA = [int](SqlOne "SELECT id FROM inventory_stock_take WHERE take_no='$pA'")
$idB = [int](SqlOne "SELECT id FROM inventory_stock_take WHERE take_no='$pB'")
Info "probe rows: A(id=$idA, DRAFT)  B(id=$idB, AUDITED)"

# C1: three parallel cancels on the same DRAFT doc -> exactly one may win
$jobs = @()
foreach ($n in 1..3) {
  $jobs += Start-Job -ScriptBlock {
    param($base, $token, $id)
    $h = @{ Authorization = $token }
    try { $r = Invoke-RestMethod -Uri "$base/inventory/stock-take/$id/cancel" -Method Post -Headers $h; return [string]$r.code }
    catch { $c = -1; try { if ($_.Exception.Response) { $c = [int]$_.Exception.Response.StatusCode } } catch { }; return [string]$c }
  } -ArgumentList $BASE, $TOKEN, $idA
}
$res = @($jobs | Wait-Job | Receive-Job)
$jobs | Remove-Job
$okCount = @($res | Where-Object { "$_" -eq '200' }).Count
if ($okCount -eq 1) { Ok ("concurrent cancel x3 -> exactly 1 succeeded (results: " + ($res -join ',') + ")") }
else { Bad ("concurrent cancel x3 -> $okCount succeeded, expect exactly 1 (results: " + ($res -join ',') + ")") }
$stA = SqlOne "SELECT status FROM inventory_stock_take WHERE id=$idA"
if ($stA -eq 'CANCELLED') { Ok "probe A ended as CANCELLED" } else { Bad "probe A status=$stA (expect CANCELLED)" }

# C2: cancel must NOT overwrite a non-draft document (the core F7-50 risk)
$rB = Api 'Post' "$BASE/inventory/stock-take/$idB/cancel" $null
$stB = SqlOne "SELECT status FROM inventory_stock_take WHERE id=$idB"
if ((Rejected $rB) -and $stB -eq 'AUDITED') {
  Ok ("cancel on an AUDITED doc rejected and status untouched ($stB): " + (MsgOf $rB))
} else {
  Bad ("cancel overwrote a non-draft doc: code=" + (CodeOf $rB) + " status=$stB")
}

# C3: sampled no-regression on a module fixed here -- cancel of an AUDITED outsource delivery must be rejected
if ($dlv -gt 0) {
  $r = Api 'Put' "$BASE/outsource/delivery/$dlv/cancel" $null
  $st = SqlOne "SELECT status FROM outsource_delivery WHERE id=$dlv"
  if ((Rejected $r) -and $st -eq 'AUDITED') { Ok ("outsource delivery cancel rejected, status untouched ($st)") }
  else { Bad ("outsource delivery cancel: code=" + (CodeOf $r) + " status=$st") }
}

# ================= F7-51 =================
Write-Output '=== F7-51: ReturnSort canonical unAudit + legacy cancel alias ==='
$rs = [int](SqlOne "SELECT IFNULL(MIN(id),0) FROM return_sort WHERE status='DRAFT'")
if ($rs -gt 0) {
  $ra = Api 'Put' "$BASE/inventory/return-sort/$rs/un-audit" $null
  $rb = Api 'Put' "$BASE/inventory/return-sort/$rs/cancel" $null
  if ((CodeOf $ra) -ne '404' -and (CodeOf $ra) -ne '-1') { Ok ("canonical /un-audit route exists (code=" + (CodeOf $ra) + ")") }
  else { Bad ("canonical /un-audit route missing: code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra)) }
  if ((CodeOf $ra) -eq (CodeOf $rb) -and (CodeOf $ra) -ne '200') {
    Ok ("/un-audit and /cancel behave identically on a DRAFT doc (both " + (CodeOf $ra) + "): " + (MsgOf $ra))
  } else {
    Bad ("alias mismatch: un-audit=" + (CodeOf $ra) + " cancel=" + (CodeOf $rb))
  }
  $st = SqlOne "SELECT status FROM return_sort WHERE id=$rs"
  if ($st -eq 'DRAFT') { Ok "DRAFT return_sort untouched by the probes" } else { Bad "return_sort status=$st (expect DRAFT)" }
} else { Info 'no DRAFT return_sort fixture; skipped' }

# ================= cleanup + self-check =================
Write-Output '=== cleanup ==='
Sql "DELETE FROM inventory_stock_take WHERE id IN ($idA,$idB)" | Out-Null
$left = [int](SqlOne "SELECT COUNT(*) FROM inventory_stock_take WHERE take_no LIKE 'PD-V50%'")
if ($left -eq 0) { Ok 'probe rows deleted' } else { Bad "$left probe row(s) left behind" }

$drift = 0
foreach ($t in $TABLES) {
  $now = [int](SqlOne "SELECT COUNT(*) FROM $t")
  if ($now -ne $counts[$t]) { Bad ("row count drifted: $t " + $counts[$t] + " -> $now"); $drift++ }
}
if ($drift -eq 0) { Ok ("row counts unchanged for all " + $TABLES.Count + " tables") }
$oiFinal = SqlOne "SELECT IFNULL(received_quantity,0) FROM outsource_material_order_item WHERE id=$probeOi"
if ((D $oiFinal) -eq (D $oiBase)) { Ok "probe material_order_item restored" } else { Bad "material_order_item drifted: $oiFinal vs $oiBase" }

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures)' } else { Write-Output ("RESULT FAIL (" + $script:fails + " failures)") }
