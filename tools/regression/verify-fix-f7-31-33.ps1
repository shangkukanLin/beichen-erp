# verify-fix-f7-31-33.ps1  (regression for the F7-31 / F7-32 / F7-33 fixes)
#
# F7-31/32: writing off another partner's receivable/payable must be REJECTED
#           (same-subjectType is no longer enough; the partner id must match).
# F7-33:    a negative item amount must be REJECTED at create AND at audit
#           (negative used to reverse-adjust the ledger: paid went negative, unpaid inflated).
#
# Negative assertions are paired with POSITIVE CONTROLS (same customer / same supplier /
# positive amount must still work end-to-end, including un-audit rollback), plus two
# "legacy dirty draft" cases corrupted via SQL to exercise the audit-side backstops.
# Everything is backed up and restored; final self-check asserts equal money + row counts.
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$ACC   = 36        # fixture account (CASH-01)
$RC_ID = 174       # receivable owned by customer 16 (200 unpaid)
$RC_OWNER = 16     # its owner
$OTHER_CUST = 24   # a different customer
$PAY_ID = 456      # payable owned by supplier 26 (48 unpaid)
$PAY_OWNER = 26    # its owner
$OTHER_SUP = 34    # a different supplier

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
$today = Get-Date -Format 'yyyy-MM-dd'
function RcBody([int]$cust, [int]$recId, $amt) {
  return @{ customerId = $cust; subjectType = 'CUSTOMER'; accountId = $ACC; receiptDate = $today
            remark = 'VERIFY-F7F'; items = @(@{ receivableId = $recId; receivableBillNo = ''; thisAmount = $amt }) }
}
function PyBody([int]$sup, [int]$payId, $amt) {
  return @{ supplierId = $sup; accountId = $ACC; paymentDate = $today; remark = 'VERIFY-F7F'
            items = @(@{ payableId = $payId; payableBillNo = ''; thisAmount = $amt }) }
}
function MaxId([string]$t) { return [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM $t") }

# ---------------- baseline ----------------
Write-Output '=== 0) baseline ==='
$rcA  = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
$pyA  = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
$maxRc  = MaxId 'finance_receipt';        $maxRcI = MaxId 'finance_receipt_item'
$maxPy  = MaxId 'finance_payment';        $maxPyI = MaxId 'finance_payment_item'
$maxCf  = MaxId 'finance_cashflow';       $maxSt  = MaxId 'finance_settlement'
$cRc0 = SqlOne 'SELECT COUNT(*) FROM finance_receipt';  $cPy0 = SqlOne 'SELECT COUNT(*) FROM finance_payment'
$cCf0 = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'; $cSt0 = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
Info "receivable $RC_ID=$rcA   payable $PAY_ID=$pyA"
Info "counts receipt=$cRc0 payment=$cPy0 cashflow=$cCf0 settlement=$cSt0"
if ($rcA -eq '' -or $pyA -eq '') { Bad 'fixture rows missing -> abort'; exit 1 }

$rcIds = @(); $pyIds = @()
function LastId([string]$t, [string]$remark) { return [int](SqlOne "SELECT id FROM $t WHERE remark='$remark' ORDER BY id DESC LIMIT 1") }

try {
  # ================= F7-31 : cross-customer write-off must be rejected =================
  Write-Output '--- F7-31 a) receipt of customer 24 -> receivable 174 (customer 16) must be REJECTED at audit ---'
  $r = Api 'POST' "$BASE/finance/receipt" (RcBody $OTHER_CUST $RC_ID 50)
  if ((CodeOf $r) -eq '200') {
    $rid = LastId 'finance_receipt' 'VERIFY-F7F'
    $rcIds += $rid
    $ra = Api 'PUT' "$BASE/finance/receipt/$rid/audit" $null
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
    if (Rejected $ra) { Ok ("audit rejected: " + (MsgOf $ra)) } else { Bad 'cross-customer write-off was ACCEPTED (fix not effective)' }
    if ($after -eq $rcA) { Ok "receivable untouched ($after)" } else { Bad "receivable changed: $rcA -> $after" }
  } else { Bad ("draft create failed (unexpected): " + (MsgOf $r)) }

  # ================= F7-32 : cross-supplier write-off must be rejected =================
  Write-Output '--- F7-32 a) payment of supplier 34 -> payable 456 (supplier 26) must be REJECTED at audit ---'
  $p = Api 'POST' "$BASE/finance/payment" (PyBody $OTHER_SUP $PAY_ID 10)
  if ((CodeOf $p) -eq '200') {
    $payId = LastId 'finance_payment' 'VERIFY-F7F'
    $pyIds += $payId
    $pa = Api 'PUT' "$BASE/finance/payment/$payId/audit" $null
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
    if (Rejected $pa) { Ok ("audit rejected: " + (MsgOf $pa)) } else { Bad 'cross-supplier write-off was ACCEPTED (fix not effective)' }
    if ($after -eq $pyA) { Ok "payable untouched ($after)" } else { Bad "payable changed: $pyA -> $after" }
  } else { Bad ("draft create failed (unexpected): " + (MsgOf $p)) }

  # ================= F7-33 : negative amount must be rejected =================
  Write-Output '--- F7-33 a) negative receipt amount must be REJECTED at create ---'
  $beforeRc = SqlOne 'SELECT COUNT(*) FROM finance_receipt'
  $n1 = Api 'POST' "$BASE/finance/receipt" (RcBody $RC_OWNER $RC_ID -50)
  if (Rejected $n1) { Ok ("rejected: " + (MsgOf $n1)) } else {
    Bad 'negative receipt amount ACCEPTED at create'
    $rcIds += (LastId 'finance_receipt' 'VERIFY-F7F')
  }
  if ($beforeRc -eq (SqlOne 'SELECT COUNT(*) FROM finance_receipt')) { Ok 'no document created for the rejected request' } else { Bad 'a document was created anyway' }

  Write-Output '--- F7-33 b) negative payment amount must be REJECTED at create ---'
  $beforePy = SqlOne 'SELECT COUNT(*) FROM finance_payment'
  $n2 = Api 'POST' "$BASE/finance/payment" (PyBody $PAY_OWNER $PAY_ID -10)
  if (Rejected $n2) { Ok ("rejected: " + (MsgOf $n2)) } else {
    Bad 'negative payment amount ACCEPTED at create'
    $pyIds += (LastId 'finance_payment' 'VERIFY-F7F')
  }
  if ($beforePy -eq (SqlOne 'SELECT COUNT(*) FROM finance_payment')) { Ok 'no document created for the rejected request' } else { Bad 'a document was created anyway' }

  # ================= POSITIVE CONTROLS =================
  Write-Output '--- CONTROL A) same-customer receipt 50 must still audit and roll back ---'
  $r = Api 'POST' "$BASE/finance/receipt" (RcBody $RC_OWNER $RC_ID 50)
  if ((CodeOf $r) -ne '200') { Bad ("valid receipt create failed: " + (MsgOf $r)) } else {
    $rid = LastId 'finance_receipt' 'VERIFY-F7F'
    $rcIds += $rid
    $ra = Api 'PUT' "$BASE/finance/receipt/$rid/audit" $null
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_receivable WHERE id=$RC_ID"
    if ((CodeOf $ra) -eq '200' -and $after -eq '50.0000|150.0000') { Ok "audit ok, receivable $rcA -> $after" }
    else { Bad ("positive receipt wrong: code=" + (CodeOf $ra) + " msg=" + (MsgOf $ra) + " rc=$after") }
    $ru = Api 'PUT' "$BASE/finance/receipt/$rid/un-audit" $null
    $back = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
    if ((CodeOf $ru) -eq '200' -and $back -eq $rcA) { Ok "un-audit restored the receivable ($back)" }
    else { Bad ("un-audit problem: code=" + (CodeOf $ru) + " rc=$back (expect $rcA)") }
  }

  Write-Output '--- CONTROL B) same-supplier payment 10 must still audit and roll back ---'
  $p = Api 'POST' "$BASE/finance/payment" (PyBody $PAY_OWNER $PAY_ID 10)
  if ((CodeOf $p) -ne '200') { Bad ("valid payment create failed: " + (MsgOf $p)) } else {
    $payId = LastId 'finance_payment' 'VERIFY-F7F'
    $pyIds += $payId
    $pa = Api 'PUT' "$BASE/finance/payment/$payId/audit" $null
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_payable WHERE id=$PAY_ID"
    if ((CodeOf $pa) -eq '200' -and $after -eq '10.0000|38.0000') { Ok "audit ok, payable $pyA -> $after" }
    else { Bad ("positive payment wrong: code=" + (CodeOf $pa) + " msg=" + (MsgOf $pa) + " py=$after") }
    $ru = Api 'PUT' "$BASE/finance/payment/$payId/un-audit" $null
    $back = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
    if ((CodeOf $ru) -eq '200' -and $back -eq $pyA) { Ok "un-audit restored the payable ($back)" }
    else { Bad ("un-audit problem: code=" + (CodeOf $ru) + " py=$back (expect $pyA)") }
  }

  # ================= LEGACY DIRTY DRAFTS (audit-side backstops) =================
  Write-Output '--- BACKSTOP 1) draft corrupted via SQL to another customer must be refused at audit ---'
  $r = Api 'POST' "$BASE/finance/receipt" (RcBody $RC_OWNER $RC_ID 30)
  if ((CodeOf $r) -ne '200') { Bad ("draft create failed: " + (MsgOf $r)) } else {
    $rid = LastId 'finance_receipt' 'VERIFY-F7F'
    $rcIds += $rid
    Sql "UPDATE finance_receipt SET customer_id=$OTHER_CUST WHERE id=$rid" | Out-Null
    $ra = Api 'PUT' "$BASE/finance/receipt/$rid/audit" $null
    $st = SqlOne "SELECT status FROM finance_receipt WHERE id=$rid"
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
    if ((Rejected $ra) -and $st -eq 'DRAFT' -and $after -eq $rcA) { Ok ("refused (code=" + (CodeOf $ra) + "), stays DRAFT, receivable untouched") }
    else { Bad ("backstop 1 failed: code=" + (CodeOf $ra) + " status=$st rc=$after (expect $rcA)") }
  }

  Write-Output '--- BACKSTOP 2) draft corrupted via SQL to a negative amount must be refused at audit ---'
  $r = Api 'POST' "$BASE/finance/receipt" (RcBody $RC_OWNER $RC_ID 30)
  if ((CodeOf $r) -ne '200') { Bad ("draft create failed: " + (MsgOf $r)) } else {
    $rid = LastId 'finance_receipt' 'VERIFY-F7F'
    $rcIds += $rid
    Sql "UPDATE finance_receipt_item SET this_amount=-30 WHERE receipt_id=$rid" | Out-Null
    $ra = Api 'PUT' "$BASE/finance/receipt/$rid/audit" $null
    $st = SqlOne "SELECT status FROM finance_receipt WHERE id=$rid"
    $after = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
    if ((Rejected $ra) -and $st -eq 'DRAFT' -and $after -eq $rcA) { Ok ("refused (code=" + (CodeOf $ra) + "), stays DRAFT, receivable untouched") }
    else { Bad ("backstop 2 failed: code=" + (CodeOf $ra) + " status=$st rc=$after (expect $rcA)") }
  }
} finally {
  Write-Output '=== cleanup ==='
  # restore money columns, then drop everything created after the baseline (dependency order)
  $p1 = $rcA -split '\|'; Sql "UPDATE finance_receivable SET paid_amount=$($p1[0]), unpaid_amount=$($p1[1]), status='$($p1[2])' WHERE id=$RC_ID" | Out-Null
  $p2 = $pyA -split '\|'; Sql "UPDATE finance_payable SET paid_amount=$($p2[0]), unpaid_amount=$($p2[1]), status='$($p2[2])' WHERE id=$PAY_ID" | Out-Null
  Sql "DELETE FROM finance_cashflow WHERE id > $maxCf" | Out-Null
  Sql "DELETE FROM finance_settlement WHERE id > $maxSt" | Out-Null
  Sql "DELETE FROM finance_receipt_item WHERE id > $maxRcI" | Out-Null
  Sql "DELETE FROM finance_receipt WHERE id > $maxRc" | Out-Null
  Sql "DELETE FROM finance_payment_item WHERE id > $maxPyI" | Out-Null
  Sql "DELETE FROM finance_payment WHERE id > $maxPy" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $aRc = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$RC_ID"
  $aPy = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAY_ID"
  $cRc = SqlOne 'SELECT COUNT(*) FROM finance_receipt';  $cPy = SqlOne 'SELECT COUNT(*) FROM finance_payment'
  $cCf = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'; $cSt = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
  $cLeak = SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE remark='VERIFY-F7F'"
  if ($aRc -eq $rcA -and $aPy -eq $pyA) { Ok "money restored (rc=$aRc ; py=$aPy)" } else { Bad "money drifted (rc $aRc vs $rcA ; py $aPy vs $pyA)" }
  if ($cRc -eq $cRc0 -and $cPy -eq $cPy0 -and $cCf -eq $cCf0 -and $cSt -eq $cSt0) {
    Ok "row counts restored (receipt=$cRc payment=$cPy cashflow=$cCf settlement=$cSt)"
  } else { Bad "counts drifted (rc=$cRc/$cRc0 py=$cPy/$cPy0 cf=$cCf/$cCf0 st=$cSt/$cSt0)" }
  if ($cLeak -eq '0') { Ok 'no probe document left behind' } else { Bad "$cLeak probe document(s) left behind" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
