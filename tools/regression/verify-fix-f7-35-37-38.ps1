# verify-fix-f7-35-37-38.ps1  (regression for the F7-35 / F7-37 / F7-38 fixes)
#
# F7-35: un-auditing a receipt/payment must recompute the ledger status with THREE states.
#        Before: `CASE WHEN unpaid+amt <= 0 THEN SETTLED ELSE UNSETTLED` -- a ledger that still had
#        other settlements came back as UNSETTLED instead of PARTIAL (proved live in 10.3: 282).
# F7-37: a receipt/payment item that carries no (or a dangling) ledger id used to be SILENTLY
#        SKIPPED while the cashflow still booked the FULL amount ("money in, ledger untouched").
#        Now create + audit both refuse it.
# F7-38: POST /finance/bill/auto-generate used to sweep EVERY enabled company (cross-tenant batch
#        write) behind a mere finance:bill page code. Now it runs the CURRENT company only;
#        the all-company sweep requires the super_admin role plus an explicit all=true.
#
# Everything is restored at the end (asserted against the baseline). ASCII-only on purpose.

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$ACC   = 36          # CASH-01 (healthy balance)
$AMT_A = 10
$AMT_B = 20

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
function D($s) { if ($null -eq $s -or "$s" -eq '') { return [decimal]0 }; return [decimal]$s }
function CodeOf($r) { if ($null -eq $r) { return '' } return [string]$r.code }
function MsgOf($r)  { if ($null -eq $r) { return '' } return [string]$r.msg }
function Rejected($r) { return ((CodeOf $r) -ne '200') }
function MaxId([string]$t) { return [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM $t") }
function LastId([string]$t) { return [int](SqlOne "SELECT id FROM $t WHERE remark='VERIFY-F7F3' ORDER BY id DESC LIMIT 1") }
# returns 'OK' when the condition holds, else the actual `paid|unpaid|status` tuple (for the message)
function Ledger([string]$table, [int]$rowId, [string]$cond) {
  return (SqlOne "SELECT CASE WHEN $cond THEN 'OK' ELSE CONCAT(IFNULL(paid_amount,''),'|',IFNULL(unpaid_amount,''),'|',IFNULL(status,'')) END FROM $table WHERE id=$rowId")
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
$today = Get-Date -Format 'yyyy-MM-dd'
function RcBody([int]$cust, $recId, $amt) {
  return @{ customerId = $cust; subjectType = 'CUSTOMER'; accountId = $ACC; receiptDate = $today
            remark = 'VERIFY-F7F3'; items = @(@{ receivableId = $recId; receivableBillNo = ''; thisAmount = $amt }) }
}
function PyBody([int]$sup, $payId, $amt) {
  return @{ supplierId = $sup; accountId = $ACC; paymentDate = $today; remark = 'VERIFY-F7F3'
            items = @(@{ payableId = $payId; payableBillNo = ''; thisAmount = $amt }) }
}

# ---------------- baseline + fixtures ----------------
Write-Output '=== 0) baseline & fixtures ==='
$R = [int](SqlOne "SELECT id FROM finance_receivable WHERE subject_type='CUSTOMER' AND status='UNSETTLED' AND unpaid_amount=amount AND amount>100 AND customer_id IS NOT NULL ORDER BY id LIMIT 1")
$RCUST = [int](SqlOne "SELECT customer_id FROM finance_receivable WHERE id=$R")
$RAMT = SqlOne "SELECT unpaid_amount FROM finance_receivable WHERE id=$R"
$P_PART = [int](SqlOne "SELECT id FROM finance_payable WHERE status='PARTIAL' AND paid_amount>0 AND unpaid_amount>0 AND amount>$AMT_B AND IFNULL(transferred_to_receivable,0)<>1 ORDER BY id LIMIT 1")
$P_OPEN = [int](SqlOne "SELECT id FROM finance_payable WHERE status='UNSETTLED' AND unpaid_amount=amount AND amount>$AMT_B AND IFNULL(transferred_to_receivable,0)<>1 ORDER BY id LIMIT 1")
$P_PART_SUP = [int](SqlOne "SELECT supplier_id FROM finance_payable WHERE id=$P_PART")
$P_OPEN_SUP = [int](SqlOne "SELECT supplier_id FROM finance_payable WHERE id=$P_OPEN")
$rBase = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$R"
$ppBase = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$P_PART"
$poBase = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$P_OPEN"
Info "receivable $R (customer $RCUST, unpaid $RAMT) = $rBase"
Info "payable PARTIAL $P_PART (sup $P_PART_SUP) = $ppBase ; payable UNSETTLED $P_OPEN (sup $P_OPEN_SUP) = $poBase"
if ($R -le 0 -or $P_PART -le 0 -or $P_OPEN -le 0) { Bad 'fixtures missing -> abort'; exit 1 }
$maxRc = MaxId 'finance_receipt'; $maxRcI = MaxId 'finance_receipt_item'
$maxPy = MaxId 'finance_payment'; $maxPyI = MaxId 'finance_payment_item'
$maxCf = MaxId 'finance_cashflow'; $maxSt = MaxId 'finance_settlement'
$maxBill = MaxId 'finance_bill';  $maxBillI = MaxId 'finance_bill_item'
$cBillOther0 = SqlOne "SELECT COUNT(*) FROM finance_bill WHERE IFNULL(company_id,0)<>1"
$cRc0 = SqlOne 'SELECT COUNT(*) FROM finance_receipt'; $cPy0 = SqlOne 'SELECT COUNT(*) FROM finance_payment'
$cCf0 = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'; $cSt0 = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
Info "counts receipt=$cRc0 payment=$cPy0 cashflow=$cCf0 settlement=$cSt0 bill_other_company=$cBillOther0"

try {
  # ================= F7-35 (receipt side) =================
  Write-Output '=== 1) F7-35 receipt: un-audit must recompute PARTIAL / UNSETTLED correctly ==='
  $rA = Api 'POST' "$BASE/finance/receipt" (RcBody $RCUST $R $AMT_A)
  $idA = LastId 'finance_receipt'
  if ((CodeOf $rA) -ne '200') { Bad ("receipt A create failed: " + (MsgOf $rA)) } else {
    $null = Api 'PUT' "$BASE/finance/receipt/$idA/audit" $null
    $t = Ledger 'finance_receivable' $R "paid_amount = $AMT_A AND unpaid_amount = amount - $AMT_A AND status = 'PARTIAL'"
    if ($t -eq 'OK') { Ok "after receipt A ($AMT_A): PARTIAL (paid $AMT_A, unpaid amount-$AMT_A)" } else { Bad "unexpected ledger after receipt A: $t" }
  }
  $rB = Api 'POST' "$BASE/finance/receipt" (RcBody $RCUST $R $AMT_B)
  $idB = LastId 'finance_receipt'
  if ((CodeOf $rB) -ne '200') { Bad ("receipt B create failed: " + (MsgOf $rB)) } else {
    $null = Api 'PUT' "$BASE/finance/receipt/$idB/audit" $null
    $t = Ledger 'finance_receivable' $R "paid_amount = $($AMT_A + $AMT_B) AND unpaid_amount = amount - $($AMT_A + $AMT_B) AND status = 'PARTIAL'"
    if ($t -eq 'OK') { Ok "after receipt B ($AMT_B): PARTIAL (paid $($AMT_A + $AMT_B))" } else { Bad "unexpected ledger after receipt B: $t" }
    # core F7-35 assertion: un-auditing B leaves A's partial settlement -> MUST stay PARTIAL
    $uB = Api 'PUT' "$BASE/finance/receipt/$idB/un-audit" $null
    $t = Ledger 'finance_receivable' $R "paid_amount = $AMT_A AND unpaid_amount = amount - $AMT_A AND status = 'PARTIAL'"
    if ((CodeOf $uB) -eq '200' -and $t -eq 'OK') { Ok "un-audit B: still PARTIAL with A's $AMT_A kept (F7-35 core)" } else { Bad "un-audit B wrong: code=$(CodeOf $uB) ledger=$t (expect PARTIAL, paid $AMT_A)" }
  }
  $uA = Api 'PUT' "$BASE/finance/receipt/$idA/un-audit" $null
  $t = Ledger 'finance_receivable' $R "paid_amount = 0 AND unpaid_amount = amount AND status = 'UNSETTLED'"
  if ((CodeOf $uA) -eq '200' -and $t -eq 'OK') { Ok "un-audit A: back to UNSETTLED (no over-correction)" } else { Bad "un-audit A wrong: code=$(CodeOf $uA) ledger=$t (expect UNSETTLED, 0/amount)" }

  Write-Output '--- full settlement then un-audit ---'
  $rC = Api 'POST' "$BASE/finance/receipt" (RcBody $RCUST $R ([decimal]$RAMT))
  $idC = LastId 'finance_receipt'
  if ((CodeOf $rC) -ne '200') { Bad ("receipt C create failed: " + (MsgOf $rC)) } else {
    $null = Api 'PUT' "$BASE/finance/receipt/$idC/audit" $null
    $t = Ledger 'finance_receivable' $R "unpaid_amount = 0 AND status = 'SETTLED'"
    if ($t -eq 'OK') { Ok "full $RAMT settlement -> SETTLED" } else { Bad "full settlement wrong: $t" }
    $null = Api 'PUT' "$BASE/finance/receipt/$idC/un-audit" $null
    $t = Ledger 'finance_receivable' $R "paid_amount = 0 AND unpaid_amount = amount AND status = 'UNSETTLED'"
    if ($t -eq 'OK') { Ok "un-audit of the full settlement -> UNSETTLED" } else { Bad "un-audit after full settlement wrong: $t" }
  }

  # ================= F7-35 (payment side) =================
  Write-Output '=== 2) F7-35 payment: same three-state recompute ==='
  $pA = Api 'POST' "$BASE/finance/payment" (PyBody $P_PART_SUP $P_PART $AMT_A)
  $pidA = LastId 'finance_payment'
  if ((CodeOf $pA) -ne '200') { Bad ("payment on PARTIAL payable create failed: " + (MsgOf $pA)) } else {
    $null = Api 'PUT' "$BASE/finance/payment/$pidA/audit" $null
    $u = Api 'PUT' "$BASE/finance/payment/$pidA/un-audit" $null
    $ppPaid = ($ppBase -split '\|')[0]; $ppUnpaid = ($ppBase -split '\|')[1]
    $t = Ledger 'finance_payable' $P_PART "paid_amount = $ppPaid AND unpaid_amount = $ppUnpaid AND status = 'PARTIAL'"
    if ((CodeOf $u) -eq '200' -and $t -eq 'OK') { Ok "payable $P_PART un-audit: restored $ppBase and stays PARTIAL (F7-35 core)" } else { Bad "payable un-audit wrong: code=$(CodeOf $u) ledger=$t (expect $ppBase)" }
  }
  $pB = Api 'POST' "$BASE/finance/payment" (PyBody $P_OPEN_SUP $P_OPEN $AMT_A)
  $pidB = LastId 'finance_payment'
  if ((CodeOf $pB) -ne '200') { Bad ("payment on UNSETTLED payable create failed: " + (MsgOf $pB)) } else {
    $null = Api 'PUT' "$BASE/finance/payment/$pidB/audit" $null
    $u = Api 'PUT' "$BASE/finance/payment/$pidB/un-audit" $null
    $t = Ledger 'finance_payable' $P_OPEN "paid_amount = 0 AND unpaid_amount = amount AND status = 'UNSETTLED'"
    if ((CodeOf $u) -eq '200' -and $t -eq 'OK') { Ok "payable $P_OPEN un-audit: back to UNSETTLED (no over-correction)" } else { Bad "payable un-audit wrong: code=$(CodeOf $u) ledger=$t (expect UNSETTLED, 0/amount)" }
  }

  # ================= F7-37 =================
  Write-Output '=== 3) F7-37 item without a ledger must be refused (create) ==='
  $cRcBefore = SqlOne 'SELECT COUNT(*) FROM finance_receipt'
  $badBody = @{ customerId = $RCUST; subjectType = 'CUSTOMER'; accountId = $ACC; receiptDate = $today
                remark = 'VERIFY-F7F3'; items = @(@{ receivableBillNo = ''; thisAmount = 50 }) }
  $n1 = Api 'POST' "$BASE/finance/receipt" $badBody
  if ((Rejected $n1) -and ("$(MsgOf $n1)" -match '未关联应收台账')) { Ok ("receipt without receivableId refused: " + (MsgOf $n1)) }
  else { Bad ("receipt without receivableId NOT refused: code=" + (CodeOf $n1) + " msg=" + (MsgOf $n1)) }
  if ($cRcBefore -eq (SqlOne 'SELECT COUNT(*) FROM finance_receipt')) { Ok 'no receipt row created' } else { Bad 'a receipt row was created anyway' }

  $cPyBefore = SqlOne 'SELECT COUNT(*) FROM finance_payment'
  $badPay = @{ supplierId = $P_PART_SUP; accountId = $ACC; paymentDate = $today; remark = 'VERIFY-F7F3'
               items = @(@{ payableBillNo = ''; thisAmount = 50 }) }
  $n2 = Api 'POST' "$BASE/finance/payment" $badPay
  if ((Rejected $n2) -and ("$(MsgOf $n2)" -match '未关联应付台账')) { Ok ("payment without payableId refused: " + (MsgOf $n2)) }
  else { Bad ("payment without payableId NOT refused: code=" + (CodeOf $n2) + " msg=" + (MsgOf $n2)) }
  if ($cPyBefore -eq (SqlOne 'SELECT COUNT(*) FROM finance_payment')) { Ok 'no payment row created' } else { Bad 'a payment row was created anyway' }

  Write-Output '--- audit backstop: legacy draft corrupted via SQL must be refused too ---'
  $d1 = Api 'POST' "$BASE/finance/receipt" (RcBody $RCUST $R $AMT_A)
  $did1 = LastId 'finance_receipt'
  if ((CodeOf $d1) -eq '200') {
    Sql "UPDATE finance_receipt_item SET receivable_id = NULL WHERE receipt_id=$did1" | Out-Null
    $a1 = Api 'PUT' "$BASE/finance/receipt/$did1/audit" $null
    $st = SqlOne "SELECT status FROM finance_receipt WHERE id=$did1"
    $cf = SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no=(SELECT code FROM finance_receipt WHERE id=$did1)"
    if ((Rejected $a1) -and $st -eq 'DRAFT' -and (D $cf) -eq 0) { Ok "null ledger id at audit refused, stays DRAFT, no cashflow" }
    else { Bad "audit backstop (null) failed: code=$(CodeOf $a1) status=$st cashflow=$cf" }
    Sql "UPDATE finance_receipt_item SET receivable_id = 999999 WHERE receipt_id=$did1" | Out-Null
    $a2 = Api 'PUT' "$BASE/finance/receipt/$did1/audit" $null
    if ((Rejected $a2) -and ("$(MsgOf $a2)" -match '不存在')) { Ok ("dangling ledger id at audit refused: " + (MsgOf $a2)) }
    else { Bad ("audit backstop (dangling) failed: code=" + (CodeOf $a2) + " msg=" + (MsgOf $a2)) }
  } else { Bad ("draft for backstop failed: " + (MsgOf $d1)) }

  $d2 = Api 'POST' "$BASE/finance/payment" (PyBody $P_PART_SUP $P_PART $AMT_A)
  $did2 = LastId 'finance_payment'
  if ((CodeOf $d2) -eq '200') {
    Sql "UPDATE finance_payment_item SET payable_id = NULL WHERE payment_id=$did2" | Out-Null
    $a3 = Api 'PUT' "$BASE/finance/payment/$did2/audit" $null
    $st2 = SqlOne "SELECT status FROM finance_payment WHERE id=$did2"
    if ((Rejected $a3) -and $st2 -eq 'DRAFT') { Ok "payment with null ledger id at audit refused, stays DRAFT" }
    else { Bad "payment audit backstop failed: code=$(CodeOf $a3) status=$st2" }
  } else { Bad ("payment draft for backstop failed: " + (MsgOf $d2)) }

  Write-Output '--- invariant: cashflow amount == sum of effective settlements (on freshly AUDITED docs) ---'
  $invR = Api 'POST' "$BASE/finance/receipt" (RcBody $RCUST $R $AMT_A)
  $invRid = LastId 'finance_receipt'
  $invP = Api 'POST' "$BASE/finance/payment" (PyBody $P_PART_SUP $P_PART $AMT_A)
  $invPid = LastId 'finance_payment'
  if ((CodeOf $invR) -eq '200' -and (CodeOf $invP) -eq '200' -and $invRid -gt 0 -and $invPid -gt 0) {
    $null = Api 'PUT' "$BASE/finance/receipt/$invRid/audit" $null
    $null = Api 'PUT' "$BASE/finance/payment/$invPid/audit" $null
    # NOTE: receipt_payment_id is SHARED by receipts and payments -- the direction filter is mandatory,
    # otherwise a RECEIPT whose id happens to equal this PAYMENT id gets summed in (observed 10=110).
    $inv1 = SqlOne "SELECT CONCAT(IFNULL(cf.income,0),'=',IFNULL((SELECT SUM(s.amount) FROM finance_settlement s WHERE s.receipt_payment_id=$invRid AND s.direction='RECEIVE' AND s.status='NORMAL'),0)) FROM finance_cashflow cf WHERE cf.related_bill_no=(SELECT code FROM finance_receipt WHERE id=$invRid)"
    if ($inv1 -match '^([0-9.]+)=\1$') { Ok "receipt cashflow income == settlements sum ($inv1)" } else { Bad "receipt invariant broken: $inv1 (a skipped item would show income>sum)" }
    $inv2 = SqlOne "SELECT CONCAT(IFNULL(cf.expense,0),'=',IFNULL((SELECT SUM(s.amount) FROM finance_settlement s WHERE s.receipt_payment_id=$invPid AND s.direction='PAY' AND s.status='NORMAL'),0)) FROM finance_cashflow cf WHERE cf.related_bill_no=(SELECT code FROM finance_payment WHERE id=$invPid)"
    if ($inv2 -match '^([0-9.]+)=\1$') { Ok "payment cashflow expense == settlements sum ($inv2)" } else { Bad "payment invariant broken: $inv2" }
  } else { Bad "invariant fixtures failed (receipt=$(CodeOf $invR) payment=$(CodeOf $invP))" }

  # ================= F7-38 =================
  Write-Output '=== 4) F7-38 auto-generate must stay inside the current company ==='
  $ag = Api 'POST' "$BASE/finance/bill/auto-generate" $null
  if ((CodeOf $ag) -eq '200') {
    $summary = [string]$ag.data
    $n = ([regex]::Matches($summary, '公司\[')).Count
    if ($n -eq 1) { Ok "summary covers exactly ONE company: $summary" } else { Bad "summary covers $n companies (expected 1): $summary" }
    $cOther = SqlOne "SELECT COUNT(*) FROM finance_bill WHERE IFNULL(company_id,0)<>1"
    if ($cOther -eq $cBillOther0) { Ok "no bill created for other companies ($cOther)" } else { Bad "other companies' bills $cBillOther0 -> $cOther" }
  } else { Bad ("auto-generate failed: code=" + (CodeOf $ag) + " msg=" + (MsgOf $ag)) }
  $agAll = Api 'POST' "$BASE/finance/bill/auto-generate?all=true" $null
  if ((CodeOf $agAll) -eq '200') {
    $summaryAll = [string]$agAll.data
    $nAll = ([regex]::Matches($summaryAll, '公司\[')).Count
    if ($nAll -ge 1) { Ok "super_admin all=true allowed, swept $nAll company(ies)" } else { Bad "all=true summary unexpected: $summaryAll" }
  } else { Bad ("super_admin all=true was refused: code=" + (CodeOf $agAll) + " msg=" + (MsgOf $agAll)) }
} finally {
  Write-Output '=== cleanup ==='
  $rB0 = $rBase -split '\|'; $ppB0 = $ppBase -split '\|'; $poB0 = $poBase -split '\|'
  Sql "UPDATE finance_receivable SET paid_amount=$($rB0[0]), unpaid_amount=$($rB0[1]), status='$($rB0[2])' WHERE id=$R" | Out-Null
  Sql "UPDATE finance_payable SET paid_amount=$($ppB0[0]), unpaid_amount=$($ppB0[1]), status='$($ppB0[2])' WHERE id=$P_PART" | Out-Null
  Sql "UPDATE finance_payable SET paid_amount=$($poB0[0]), unpaid_amount=$($poB0[1]), status='$($poB0[2])' WHERE id=$P_OPEN" | Out-Null
  Sql "DELETE FROM finance_cashflow WHERE id > $maxCf" | Out-Null
  Sql "DELETE FROM finance_settlement WHERE id > $maxSt" | Out-Null
  Sql "DELETE FROM finance_receipt_item WHERE id > $maxRcI" | Out-Null
  Sql "DELETE FROM finance_receipt WHERE id > $maxRc" | Out-Null
  Sql "DELETE FROM finance_payment_item WHERE id > $maxPyI" | Out-Null
  Sql "DELETE FROM finance_payment WHERE id > $maxPy" | Out-Null
  Sql "DELETE FROM finance_bill_item WHERE id > $maxBillI" | Out-Null
  Sql "DELETE FROM finance_bill WHERE id > $maxBill" | Out-Null

  Write-Output '=== 9) post-check (must equal baseline) ==='
  $rFin = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_receivable WHERE id=$R"
  $ppFin = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$P_PART"
  $poFin = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$P_OPEN"
  if ($rFin -eq $rBase -and $ppFin -eq $ppBase -and $poFin -eq $poBase) { Ok "ledgers restored (R=$rFin ; P=$ppFin ; P2=$poFin)" }
  else { Bad "ledger drift: R=$rFin/$rBase P=$ppFin/$ppBase P2=$poFin/$poBase" }
  $cRc = SqlOne 'SELECT COUNT(*) FROM finance_receipt'; $cPy = SqlOne 'SELECT COUNT(*) FROM finance_payment'
  $cCf = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'; $cSt = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
  $cBill = SqlOne "SELECT COUNT(*) FROM finance_bill WHERE id > $maxBill"
  if ($cRc -eq $cRc0 -and $cPy -eq $cPy0 -and $cCf -eq $cCf0 -and $cSt -eq $cSt0) {
    Ok "row counts restored (receipt=$cRc payment=$cPy cashflow=$cCf settlement=$cSt)"
  } else { Bad "counts drifted (rc=$cRc/$cRc0 py=$cPy/$cPy0 cf=$cCf/$cCf0 st=$cSt/$cSt0)" }
  if ((D $cBill) -eq 0) { Ok 'no auto-generated bill left behind' } else { Bad "$cBill auto-generated bill(s) left behind" }
  $leak = SqlOne "SELECT COUNT(*) FROM finance_receipt WHERE remark='VERIFY-F7F3'"
  if ((D $leak) -eq 0) { Ok 'no probe receipt left behind' } else { Bad "$leak probe receipt(s) left behind" }
}

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
