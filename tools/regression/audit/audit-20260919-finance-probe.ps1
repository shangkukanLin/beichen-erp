# audit-20260919-finance-probe.ps1  (Batch 3a / F7 money-irreversible evidence)
#
# Three probes on the money-mutation paths (receipt / payment):
#   A) CROSS-PARTNER SETTLEMENT (receipt): a receipt whose customerId is X may settle a
#      receivable that belongs to customer Y -- audit only compares subjectType (CUSTOMER),
#      never customerId.
#   B) NEGATIVE AMOUNT: thisAmount = -50 is accepted; unpaid grows (200 -> 250) and
#      paid_amount goes negative (-50).
#   C) CROSS-PARTNER SETTLEMENT (payment): same class on the payable side (supplierId 34
#      paying a payable that belongs to supplier 26).
#
# Fully backed up and rolled back: money columns are restored from the baseline and every row
# created after the baseline (id > baseline max) is deleted in dependency order.
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$ACC   = 36          # fixture account (CASH-01)
$RC_ID = 174         # receivable of customer 16 (200 unpaid)
$RC_ID2 = 172        # second receivable of customer 16 (negative-amount probe)
$RC_OWNER = 16       # owner of both receivables
$OTHER_CUST = 24     # a different customer that also has receivables
$PAY_ID = 456        # payable of supplier 26 (48 unpaid)
$PAY_OWNER = 26
$OTHER_SUP = 34      # different supplier (its own payables exist)

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
$today = Get-Date -Format 'yyyy-MM-dd'

# ---------------- baseline ----------------
Write-Output '=== 0) baseline ==='
$rcA = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
$rcB = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID2"
$pyA = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
$maxRc  = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_receipt'
$maxRcI = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_receipt_item'
$maxPy  = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_payment'
$maxPyI = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_payment_item'
$maxCf  = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_cashflow'
$maxSt  = SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_settlement'
$cRc0 = SqlOne 'SELECT COUNT(*) FROM finance_receipt'
$cPy0 = SqlOne 'SELECT COUNT(*) FROM finance_payment'
$cCf0 = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'
$cSt0 = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
Info "receivable $RC_ID=$rcA  $RC_ID2=$rcB   payable $PAY_ID=$pyA"
Info "counts receipt=$cRc0 payment=$cPy0 cashflow=$cCf0 settlement=$cSt0 ; maxIds rc=$maxRc/$maxRcI pay=$maxPy/$maxPyI cf=$maxCf st=$maxSt"
if ($rcA -eq '' -or $pyA -eq '') { Bad 'fixture rows missing -> abort'; exit 1 }

function RestoreReceivable([int]$id, [string]$bak) {
  $p = $bak -split '\|'
  Sql "UPDATE finance_receivable SET paid_amount=$($p[0]), unpaid_amount=$($p[1]), status='$($p[2])' WHERE id=$id" | Out-Null
}
function RestorePayable([int]$id, [string]$bak) {
  $p = $bak -split '\|'
  Sql "UPDATE finance_payable SET paid_amount=$($p[0]), unpaid_amount=$($p[1]), status='$($p[2])' WHERE id=$id" | Out-Null
}

$rcCodes = @(); $pyCodes = @()
try {
  # ============ PROBE A : cross-customer settlement on receipts ============
  Write-Output '--- PROBE A: receipt of customer 24 settles receivable 174 (customer 16) ---'
  $rBody = @{ customerId = $OTHER_CUST; subjectType = 'CUSTOMER'; accountId = $ACC; receiptDate = $today
              remark = 'AUDIT-PROBE-RC-A'; items = @(@{ receivableId = $RC_ID; receivableBillNo = ''; thisAmount = 50 }) }
  $r1 = Api 'POST' "$BASE/finance/receipt" $rBody
  Info ("create -> code=" + (CodeOf $r1) + " msg=" + (MsgOf $r1))
  if ((CodeOf $r1) -ne '200') { Bad 'receipt create rejected -> probe A inconclusive' } else {
    $rid = [int](SqlOne "SELECT id FROM finance_receipt WHERE remark='AUDIT-PROBE-RC-A' ORDER BY id DESC LIMIT 1")
    $rc     = SqlOne "SELECT code FROM finance_receipt WHERE id=$rid"
    $rcCodes += $rc
    $ra = Api 'PUT' "$BASE/finance/receipt/$rid/audit" $null
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_receivable WHERE id=$RC_ID"
    $owner = SqlOne "SELECT customer_id FROM finance_receipt WHERE id=$rid"
    Info ("audit -> code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra) + " receiptCustomer=$owner receivableOwner=$RC_OWNER")
    if ((CodeOf $ra) -eq '200' -and $after -eq '50.0000|150.0000') {
      Ok "CROSS-PARTNER SETTLEMENT: receipt of customer $OTHER_CUST settled customer $RC_OWNER receivable ($rcA -> $after)"
    } else { Bad ("probe A inconclusive: audit code=" + (CodeOf $ra) + " receivable=$after (expect 50.0000|150.0000)") }
  }

  # ============ PROBE B : negative thisAmount ============
  Write-Output '--- PROBE B: receipt item thisAmount = -50 ---'
  $rBody2 = @{ customerId = $RC_OWNER; subjectType = 'CUSTOMER'; accountId = $ACC; receiptDate = $today
               remark = 'AUDIT-PROBE-RC-B'; items = @(@{ receivableId = $RC_ID2; receivableBillNo = ''; thisAmount = -50 }) }
  $r2 = Api 'POST' "$BASE/finance/receipt" $rBody2
  Info ("create -> code=" + (CodeOf $r2) + " msg=" + (MsgOf $r2))
  if ((CodeOf $r2) -ne '200') { Bad 'negative receipt create rejected -> probe B inconclusive' } else {
    $rid2 = [int](SqlOne "SELECT id FROM finance_receipt WHERE remark='AUDIT-PROBE-RC-B' ORDER BY id DESC LIMIT 1")
    $rc2  = SqlOne "SELECT code FROM finance_receipt WHERE id=$rid2"
    $rcCodes += $rc2
    $ra2 = Api 'PUT' "$BASE/finance/receipt/$rid2/audit" $null
    $after2 = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_receivable WHERE id=$RC_ID2"
    $amt = SqlOne "SELECT amount FROM finance_receipt WHERE id=$rid2"
    Info ("audit -> code=" + (CodeOf $ra2) + " receiptAmount=$amt receivable=$after2")
    if ((CodeOf $ra2) -eq '200' -and $after2 -eq '-50.0000|250.0000') {
      Ok "NEGATIVE AMOUNT ACCEPTED: receivable $RC_ID2 went $rcB -> $after2 (paid becomes negative, unpaid inflated)"
    } else { Bad ("probe B inconclusive: audit code=" + (CodeOf $ra2) + " receivable=$after2 (expect -50.0000|250.0000)") }
  }

  # ============ PROBE C : cross-supplier settlement on payments ============
  Write-Output "--- PROBE C: payment of supplier $OTHER_SUP settles payable $PAY_ID (supplier $PAY_OWNER) ---"
  $pBody = @{ supplierId = $OTHER_SUP; accountId = $ACC; paymentDate = $today; remark = 'AUDIT-PROBE-PAY'
              items = @(@{ payableId = $PAY_ID; payableBillNo = ''; thisAmount = 10 }) }
  $p1 = Api 'POST' "$BASE/finance/payment" $pBody
  Info ("create -> code=" + (CodeOf $p1) + " msg=" + (MsgOf $p1))
  if ((CodeOf $p1) -ne '200') { Bad 'payment create rejected -> probe C inconclusive' } else {
    $payId = [int](SqlOne "SELECT id FROM finance_payment WHERE remark='AUDIT-PROBE-PAY' ORDER BY id DESC LIMIT 1")
    $pcode = SqlOne "SELECT code FROM finance_payment WHERE id=$payId"
    $pyCodes += $pcode
    $pa = Api 'PUT' "$BASE/finance/payment/$payId/audit" $null
    $after3 = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_payable WHERE id=$PAY_ID"
    $sup = SqlOne "SELECT supplier_id FROM finance_payment WHERE id=$payId"
    Info ("audit -> code=" + (CodeOf $pa) + " msg=" + (MsgOf $pa) + " paymentSupplier=$sup payableOwner=$PAY_OWNER")
    if ((CodeOf $pa) -eq '200' -and $after3 -eq '10.0000|38.0000') {
      Ok "CROSS-SUPPLIER SETTLEMENT: payment of supplier $OTHER_SUP settled supplier $PAY_OWNER payable ($pyA -> $after3)"
    } else { Bad ("probe C inconclusive/failed: audit code=" + (CodeOf $pa) + " payable=$after3 (expect 10.0000|38.0000)") }
  }
} finally {
  Write-Output '=== cleanup ==='
  # restore money columns, then drop everything created after the baseline (dependency order)
  RestoreReceivable $RC_ID  $rcA
  RestoreReceivable $RC_ID2 $rcB
  RestorePayable    $PAY_ID $pyA
  Sql "DELETE FROM finance_cashflow WHERE id > $maxCf" | Out-Null
  Sql "DELETE FROM finance_settlement WHERE id > $maxSt" | Out-Null
  Sql "DELETE FROM finance_receipt_item WHERE id > $maxRcI" | Out-Null
  Sql "DELETE FROM finance_receipt WHERE id > $maxRc" | Out-Null
  Sql "DELETE FROM finance_payment_item WHERE id > $maxPyI" | Out-Null
  Sql "DELETE FROM finance_payment WHERE id > $maxPy" | Out-Null
  Sql "DELETE FROM finance_receipt WHERE remark LIKE 'AUDIT-PROBE-%'" | Out-Null
  Sql "DELETE FROM finance_payment WHERE remark LIKE 'AUDIT-PROBE-%'" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $aA = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
  $aB = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID2"
  $aP = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
  $cRc  = SqlOne 'SELECT COUNT(*) FROM finance_receipt'
  $cPy  = SqlOne 'SELECT COUNT(*) FROM finance_payment'
  $cCf  = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'
  $cSt  = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
  $cRcI = SqlOne 'SELECT COUNT(*) FROM finance_receipt_item'
  $cPyI = SqlOne 'SELECT COUNT(*) FROM finance_payment_item'
  if ($aA -eq $rcA -and $aB -eq $rcB) { Ok "receivables restored ($aA / $aB)" } else { Bad "receivable drifted ($aA vs $rcA ; $aB vs $rcB)" }
  if ($aP -eq $pyA) { Ok "payable restored ($aP)" } else { Bad "payable drifted ($aP vs $pyA)" }
  $okCnt = ($cRc -eq $cRc0) -and ($cPy -eq $cPy0) -and ($cCf -eq $cCf0) -and ($cSt -eq $cSt0)
  if ($okCnt) { Ok "row counts restored (receipt=$cRc payment=$cPy cashflow=$cCf settlement=$cSt items=$cRcI/$cPyI)" }
  else { Bad "row counts drifted (receipt=$cRc/$cRc0 payment=$cPy/$cPy0 cashflow=$cCf/$cCf0 settlement=$cSt/$cSt0)" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
