# verify-fix-f7-39-43.ps1  (regression for batch-10: F7-39 / F7-43 / F7-46-receive)
#
# F7-39 (finance write-path hardening):
#   #1 bill generate: billType/partnerId/periodEnd became REQUIRED (a null used to skip the duplicate check)
#   #2 billType is validated against BillType enum (anything non-RECEIVABLE used to go to the payable branch)
#   #3 single-number generation: conflict check + retry instead of a silent "seq = 1" fallback;
#      finance_cashflow.flow_no and finance_expense.expense_no got UNIQUE indexes (were not indexed at all)
#   #4 payment updateAttach: transactional + CANCELLED documents are frozen
#   #5 invoice amount normalisation: amount + taxAmount == totalAmount exactly
#   #8 manual /auto-generate now takes the same cross-instance GET_LOCK as the scheduler
#   #9 ReceivableQuery.unpaid customer branch now filters subjectType=CUSTOMER (and guards null customerId)
# F7-43 (analysis service):
#   #1 summary() loads the 6 aggregations once (was profit(2) + explicit + profit(6) = 3x identical SQL)
#   #2 months input capped at 60; #3 range truncation unified to one constant with a "truncated" flag;
#   #4 pie slices no longer drop rows without product_id (they go to an "other" slice);
#   #5 TOP_N constant; #6 two dead SQLs removed
# F7-46: receive() has a server-side dedup window (same order+warehouse+type within 5s reuses the draft)
#
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
function D($s) { if ($null -eq $s -or "$s" -eq '') { return [decimal]0 }; try { return [decimal]$s } catch { return [decimal]-999999 } }
function CodeOf($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function MsgOf($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }
function Rejected($r) { return ((CodeOf $r) -ne '200') }

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

# NOTE: never name a variable $base -- PowerShell is case-insensitive, so it would clobber $BASE.
$TABLES = @('finance_bill','finance_invoice','finance_payment','finance_cashflow','finance_expense',
            'finance_receivable','finance_receipt','outsource_delivery','outsource_delivery_item',
            'outsource_material_order_item','warehouse_stock')
$counts = @{}
foreach ($t in $TABLES) { $counts[$t] = [int](SqlOne "SELECT COUNT(*) FROM $t") }

# ================= F7-39 =================
Write-Output '=== F7-39: finance write-path hardening ==='

# #1 / #2 -- invalid bill generate inputs must be rejected WITHOUT creating a bill
$b0 = [int](SqlOne "SELECT COUNT(*) FROM finance_bill")
$r1 = Api 'Post' "$BASE/finance/bill/generate" @{ partnerId = 1; periodEnd = '2026-09-30' }
$r2 = Api 'Post' "$BASE/finance/bill/generate" @{ billType = 'RECEIVABLE'; periodEnd = '2026-09-30' }
$r3 = Api 'Post' "$BASE/finance/bill/generate" @{ billType = 'XXX'; partnerId = 1; periodEnd = '2026-09-30' }
$b1 = [int](SqlOne "SELECT COUNT(*) FROM finance_bill")
if ((Rejected $r1) -and (Rejected $r2) -and (Rejected $r3)) {
  Ok ("bill generate rejected all 3 invalid inputs (null billType / null partnerId / unknown billType): " + (MsgOf $r3))
} else {
  Bad ("bill generate accepted an invalid input: " + (CodeOf $r1) + "/" + (CodeOf $r2) + "/" + (CodeOf $r3))
}
if ($b1 -eq $b0) { Ok "no finance_bill row was created by the invalid calls" } else { Bad "finance_bill grew $b0 -> $b1" }

# #3 -- the two previously unindexed single-number columns must now be UNIQUE
$idx = Sql "SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME, ' non_unique=', NON_UNIQUE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='beichen_erp' AND ((TABLE_NAME='finance_cashflow' AND COLUMN_NAME='flow_no') OR (TABLE_NAME='finance_expense' AND COLUMN_NAME='expense_no')) AND NON_UNIQUE=0"
$idxCount = @($idx -split "`n" | Where-Object { "$_".Trim() -ne '' }).Count
$dupCf = [int](SqlOne "SELECT COUNT(*) FROM (SELECT flow_no FROM finance_cashflow GROUP BY flow_no HAVING COUNT(*)>1) t")
$dupEx = [int](SqlOne "SELECT COUNT(*) FROM (SELECT expense_no FROM finance_expense GROUP BY expense_no HAVING COUNT(*)>1) t")
if ($idxCount -eq 2) { Ok "unique indexes present on finance_cashflow.flow_no and finance_expense.expense_no" }
else { Bad ("expected 2 unique indexes, found " + $idxCount) }
if ($dupCf -eq 0 -and $dupEx -eq 0) { Ok "no duplicate single numbers exist (migration-safe)" }
else { Bad "duplicates found: cashflow=$dupCf expense=$dupEx" }

# #4 -- attach on a CANCELLED payment must be rejected; an AUDITED one may still be updated (then restored)
$payCancelled = [int](SqlOne "SELECT IFNULL(MIN(id),0) FROM finance_payment WHERE status='CANCELLED'")
if ($payCancelled -gt 0) {
  $r = Api 'Put' "$BASE/finance/payment/$payCancelled/attach" @{ attachUrl = 'V39-PROBE' }
  if (Rejected $r) { Ok ("attach on CANCELLED payment rejected: " + (MsgOf $r)) } else { Bad "attach on CANCELLED payment was accepted" }
} else { Info 'no CANCELLED payment fixture' }
$payAudited = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM finance_payment WHERE status='AUDITED'")
if ($payAudited -gt 0) {
  $oldAttach = SqlOne "SELECT IFNULL(attach_url,'') FROM finance_payment WHERE id=$payAudited"
  $r = Api 'Put' "$BASE/finance/payment/$payAudited/attach" @{ attachUrl = 'V39-PROBE-A' }
  $nowAttach = SqlOne "SELECT IFNULL(attach_url,'') FROM finance_payment WHERE id=$payAudited"
  if ((CodeOf $r) -eq '200' -and $nowAttach -eq 'V39-PROBE-A') { Ok "attach on AUDITED payment still works (positive control)" }
  else { Bad ("attach positive control failed: code=" + (CodeOf $r) + " value=$nowAttach") }
  Sql "UPDATE finance_payment SET attach_url='$oldAttach' WHERE id=$payAudited" | Out-Null
  $back = SqlOne "SELECT IFNULL(attach_url,'') FROM finance_payment WHERE id=$payAudited"
  if ($back -eq $oldAttach) { Ok "payment attach_url restored" } else { Bad "attach_url not restored ($oldAttach -> $back)" }
} else { Info 'no AUDITED payment fixture' }

# #5 -- invoice: amount + taxAmount == totalAmount even when total has 3 decimals
$stamp = Get-Date -Format 'HHmmss'
$invNo = "V39-$stamp"
$r = Api 'Post' "$BASE/finance/invoice" @{ direction = 'SALE'; invoiceNo = $invNo; taxRate = 13; totalAmount = 100.005 }
if ((CodeOf $r) -eq '200') {
  $inv = SqlOne "SELECT CONCAT(amount,'|',tax_amount,'|',total_amount,'|',(IFNULL(amount,0)+IFNULL(tax_amount,0)=IFNULL(total_amount,0))) FROM finance_invoice WHERE invoice_no='$invNo' ORDER BY id DESC LIMIT 1"
  $ip = $inv -split '\|'
  if ($ip.Count -ge 4 -and $ip[3] -eq '1') { Ok ("invoice identity holds: amount=$($ip[0]) + tax=$($ip[1]) == total=$($ip[2])") }
  else { Bad ("invoice identity broken: $inv") }
  Sql "DELETE FROM finance_invoice WHERE invoice_no='$invNo'" | Out-Null
} else { Bad ("invoice create failed: code=" + (CodeOf $r) + " msg=" + (MsgOf $r)) }

# #9 -- unpaid dropdown for a customer must match the CUSTOMER-scoped DB count
$cust = [int](SqlOne "SELECT IFNULL(MAX(customer_id),0) FROM finance_receivable WHERE subject_type='CUSTOMER' AND customer_id IS NOT NULL")
if ($cust -gt 0) {
  $api = Api 'Get' "$BASE/finance/receivable/unpaid?customerId=$cust" $null
  $apiCount = 0
  if ($null -ne $api.data) { $apiCount = @($api.data).Count }
  $dbCount = [int](SqlOne ("SELECT COUNT(*) FROM finance_receivable WHERE subject_type='CUSTOMER' AND customer_id=" + $cust + " AND status NOT IN ('SETTLED','CANCELLED','ADVANCE') AND amount > 0"))
  if ($apiCount -eq $dbCount) { Ok ("unpaid(customerId=$cust) matches the CUSTOMER-scoped count ($dbCount)") }
  else { Bad ("unpaid(customerId=$cust) returned $apiCount rows, DB says $dbCount") }
  $nullCust = Api 'Get' "$BASE/finance/receivable/unpaid" $null
  $ncCount = 0
  if ($null -ne $nullCust.data) { $ncCount = @($nullCust.data).Count }
  if ($ncCount -eq 0) { Ok "unpaid() without customerId returns an empty list (null guard)" }
  else { Bad ("unpaid() without customerId returned $ncCount rows") }
} else { Info 'no CUSTOMER receivable fixture' }

# ================= F7-43 =================
Write-Output '=== F7-43: analysis service ==='

# #1 -- summary() must stay consistent with the profit endpoint (same source, refactored to one load)
$sum = Api 'Get' "$BASE/finance/analysis/summary" $null
$p2  = Api 'Get' "$BASE/finance/analysis/profit?months=2" $null
if ((CodeOf $sum) -eq '200' -and (CodeOf $p2) -eq '200') {
  $rows2 = @($p2.data.rows)
  $curRev = [string]$sum.data.cur.revenue
  $p2Rev  = if ($rows2.Count -gt 0) { [string]$rows2[$rows2.Count - 1].revenue } else { '' }
  if ($curRev -eq $p2Rev) { Ok ("summary.cur.revenue == profit(2) last row revenue ($curRev)") }
  else { Bad ("summary.cur.revenue=$curRev but profit(2) last=$p2Rev") }
  $p6 = Api 'Get' "$BASE/finance/analysis/profit?months=6" $null
  $t6 = @($p6.data.rows)
  $trend = @($sum.data.trend)
  $same = ($t6.Count -eq $trend.Count)
  if ($same) {
    for ($i = 0; $i -lt $t6.Count; $i++) {
      if ([string]$t6[$i].revenue -ne [string]$trend[$i].revenue) { $same = $false; break }
    }
  }
  if ($same) { Ok ("summary.trend matches profit(6) revenue for all " + $t6.Count + " months") }
  else { Bad "summary.trend diverged from profit(6) after the refactor" }
} else {
  Bad ("summary/profit endpoints failed: " + (CodeOf $sum) + "/" + (CodeOf $p2))
}

# #2 -- months input is capped at 60
$pm = Api 'Get' "$BASE/finance/analysis/profit?months=9999" $null
$pmLen = 0
if ($null -ne $pm.data.months) { $pmLen = @($pm.data.months).Count }
if ($pmLen -eq 60) { Ok "profit?months=9999 capped to 60 months" } else { Bad "profit?months=9999 returned $pmLen months (expect 60)" }

# #3 -- range truncation is unified (400 days) and reported
$ct = Api 'Get' "$BASE/finance/analysis/cash-trend?start=2024-01-01&end=2026-09-19" $null
if ((CodeOf $ct) -eq '200') {
  $tr = [string]$ct.data.truncated
  $md = [string]$ct.data.maxDays
  if ($tr -eq 'True' -and $md -eq '400') { Ok "cash-trend reports truncated=true / maxDays=400 for a 500+ day range" }
  else { Bad ("cash-trend truncation flags missing: truncated=$tr maxDays=$md") }
} else { Bad ("cash-trend failed: " + (CodeOf $ct) + " " + (MsgOf $ct)) }
$ok2 = Api 'Get' "$BASE/finance/analysis/overview-kpi?start=2026-09-01&end=2026-09-19" $null
if ((CodeOf $ok2) -eq '200' -and [string]$ok2.data.range.maxDays -eq '400') { Ok "overview-kpi exposes maxDays=400" }
else { Bad ("overview-kpi range flags missing: " + (CodeOf $ok2)) }

# #4 -- rows without product_id land in the "other" slice instead of being dropped
$nullProd = [int](SqlOne "SELECT COUNT(*) FROM purchase_order_item WHERE product_id IS NULL")
$pa = Api 'Get' "$BASE/finance/analysis/purchase-analysis?start=2026-09-01&end=2026-09-19" $null
if ((CodeOf $pa) -eq '200') {
  $slices = @($pa.data.directPurchaseByProduct)
  $hasOther = $false
  foreach ($s in $slices) { if ("$($s.name)" -like '*other*' -or "$($s.name)" -like '*Other*') { $hasOther = $true } }
  if ($nullProd -gt 0) {
    if ($hasOther) { Ok ("purchase pie contains an 'other' slice (DB has $nullProd NULL-product rows)") }
    else { Bad "DB has NULL-product purchase rows but the pie has no 'other' slice" }
  } else { Info "no NULL-product purchase rows in DB (slice presence not provable); slices=" + $slices.Count }
  $sumSlices = [decimal]0
  foreach ($s in $slices) { $sumSlices = $sumSlices + (D $s.value) }
  Info ("pie totals: directPurchaseAmount=" + $pa.data.pieTotals.directPurchaseAmount + " sum(slices)=" + $sumSlices)
} else { Bad ("purchase-analysis failed: " + (CodeOf $pa) + " " + (MsgOf $pa)) }

# ================= F7-46 =================
Write-Output '=== F7-46: receive dedup window ==='
# NOTE (2026-09-19, F7-66): receive() now enforces "received + this <= ordered", so the fixture must
# be an item row that still HAS remaining quota. Picking MAX(id) used to land on a fully received row
# and the first call is now correctly rejected before the dedup window is even reached.
# The assertion also changed shape: the second call is now REFUSED with an explicit error instead of
# silently returning the same draft id -- both prove "no second draft is created".
$moRow = SqlOne ("SELECT CONCAT(i.order_id,'|',i.id,'|',IFNULL(o.supplier_id,0)) FROM outsource_material_order_item i " +
                 "JOIN outsource_material_order o ON o.id=i.order_id " +
                 "WHERE o.status='RECEIVING' AND IFNULL(i.order_quantity,0)-IFNULL(i.received_quantity,0) >= 10 " +
                 "ORDER BY i.id LIMIT 1")
$fp = $moRow -split '\|'
$moId = 0; $moItem = 0; $moSup = '0'
if ($fp.Count -ge 3) { $moId = [int]$fp[0]; $moItem = [int]$fp[1]; $moSup = $fp[2] }
$wh = [int](SqlOne ("SELECT IFNULL(MAX(id),0) FROM warehouse WHERE factory_id=" + $moSup))
if ($wh -le 0) { $wh = [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM warehouse") }
$dlv0 = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery")
if ($moId -gt 0 -and $moItem -gt 0 -and $wh -gt 0) {
  Info ("fixture material_order=$moId item=$moItem warehouse=$wh (row still has remaining quota)")
  $rcvBody = @{ warehouseId = $wh; items = @(@{ itemId = $moItem; quantity = 10 }) }
  $r1 = Api 'Post' "$BASE/outsource/material-order/$moId/receive" $rcvBody
  $r2 = Api 'Post' "$BASE/outsource/material-order/$moId/receive" $rcvBody
  $dlv1 = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery")
  $created = $dlv1 - $dlv0
  if ((CodeOf $r1) -eq '200' -and (CodeOf $r2) -ne '200') {
    Ok ("second receive blocked by the dedup guard (first id=" + $r1.data + "; second: " + (MsgOf $r2) + ")")
  } elseif ((CodeOf $r1) -eq '200' -and (CodeOf $r2) -eq '200' -and [string]$r1.data -eq [string]$r2.data) {
    Ok ("second receive within 5s reused the same draft delivery (id=" + $r1.data + ")")
  } else {
    Bad ("receive calls failed: r1=" + (CodeOf $r1) + " " + (MsgOf $r1) + " | r2=" + (CodeOf $r2) + " " + (MsgOf $r2))
  }
  if ($created -eq 1) { Ok "exactly 1 delivery draft was created by the double submit (before the fix: 2)" }
  else { Bad ("$created delivery drafts created by a double submit (expect 1)") }
  # cleanup the probe delivery (only when the first call actually created one)
  if ((CodeOf $r1) -eq '200') {
    $dlvId = [int]$r1.data
    Sql "DELETE FROM outsource_delivery_item WHERE delivery_id=$dlvId" | Out-Null
    Sql "DELETE FROM outsource_delivery WHERE id=$dlvId" | Out-Null
    $left = [int](SqlOne "SELECT COUNT(*) FROM outsource_delivery WHERE id=$dlvId")
    if ($left -eq 0) { Ok "probe delivery removed" } else { Bad "probe delivery left behind" }
  }
} else { Info "receive fixture incomplete (order=$moId item=$moItem wh=$wh); skipped" }

# ================= cleanup + self-check =================
Write-Output '=== cleanup ==='
$drift = 0
foreach ($t in $TABLES) {
  $now = [int](SqlOne "SELECT COUNT(*) FROM $t")
  if ($now -ne $counts[$t]) { Bad ("row count drifted: $t " + $counts[$t] + " -> $now"); $drift++ }
}
if ($drift -eq 0) { Ok ("row counts unchanged for all " + $TABLES.Count + " tables") }

Write-Output ''
if ($script:fails -eq 0) { Write-Output 'RESULT PASS (0 failures)' } else { Write-Output ("RESULT FAIL (" + $script:fails + " failures)") }
