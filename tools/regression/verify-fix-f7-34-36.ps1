# verify-fix-f7-34-36.ps1  (regression for the F7-34 / F7-36 fixes)
#
# F7-34: every payable aggregation must use ONE rule -- rows with transferred_to_receivable=1
#        (negative refund/deduction items already turned into a supplier receivable) are EXCLUDED.
#        Before the fix the supplier page (SupplierMapper.sumPayableBalance) and 3 analysis SQLs
#        still counted them, so the supplier page differed from the payable summary by exactly 250
#        (the single transferred row 323: supplier 34, -250).
# F7-36: auditing a payment must check the account's live balance first (like the expense doc);
#        before the fix the account could be overdrawn (balance is just SUM(income-expense)).
#
# Negative assertions are paired with POSITIVE controls, and everything is restored at the end
# (asserted by re-comparing account balances + row counts with the baseline).
# ASCII-only on purpose (PowerShell 5.1 + UTF-8 BOM pitfalls).

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$SUP        = 34      # supplier whose page balance used to be understated by 250
$PAYABLE    = 282     # payable of supplier 26 (amount 2090 / unpaid 1590 / PARTIAL)
$PAY_SUP    = 26
$ACC_OK     = 36      # CASH-01 (healthy balance)
$ACC_POOR   = 38      # WX-01 (smallest balance)
$POOR_AMT   = 25000   # must exceed WX-01 balance -> overdraft attempt
$OK_AMT     = 10

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
function Bal([int]$acc) { return D (SqlOne ("SELECT IFNULL(SUM(IFNULL(income,0)-IFNULL(expense,0)),0) FROM finance_cashflow WHERE account_id=" + $acc)) }

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
function MaxId([string]$t) { return [int](SqlOne "SELECT IFNULL(MAX(id),0) FROM $t") }
function PyBody([int]$sup, [int]$acc, [int]$payableId, $amt) {
  return @{ supplierId = $sup; accountId = $acc; paymentDate = (Get-Date -Format 'yyyy-MM-dd'); remark = 'VERIFY-F7F2'
            items = @(@{ payableId = $payableId; payableBillNo = ''; thisAmount = $amt }) }
}

# ---------------- baseline ----------------
Write-Output '=== 0) baseline ==='
$supDbIncl = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE supplier_id=$SUP AND status NOT IN ('SETTLED','CANCELLED')")
$supDbExcl = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE supplier_id=$SUP AND status NOT IN ('SETTLED','CANCELLED') AND IFNULL(transferred_to_receivable,0)<>1")
$allExcl   = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1")
$agingDb   = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1")
$top5Db    = D (SqlOne "SELECT IFNULL(SUM(u),0) FROM (SELECT IFNULL(SUM(unpaid_amount),0) u FROM finance_payable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1 GROUP BY supplier_name ORDER BY u DESC LIMIT 5) t")
$pyA   = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAYABLE"
$parts = $pyA -split '\|'
$balOk0 = Bal $ACC_OK
$balPoor0 = Bal $ACC_POOR
$maxPy  = MaxId 'finance_payment';  $maxPyI = MaxId 'finance_payment_item'
$maxCf  = MaxId 'finance_cashflow'; $maxSt  = MaxId 'finance_settlement'
$cPy0 = SqlOne 'SELECT COUNT(*) FROM finance_payment'; $cCf0 = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'
$cSt0 = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
$delta = $supDbExcl - $supDbIncl
Info "supplier $SUP payable: incl_transferred=$supDbIncl  excl_transferred=$supDbExcl  delta=$delta"
Info "payable $PAYABLE = $pyA ; accounts: ACC$ACC_OK=$balOk0  ACC$ACC_POOR=$balPoor0"
Info "db all(excl transferred)=$allExcl  aging(subset)=$agingDb  top5=$top5Db"

# ---------------- F7-34 ----------------
Write-Output '=== 1) F7-34 one payable-balance rule across endpoints ==='
$pg = Api 'GET' "$BASE/supplier/page?pageNum=1&pageSize=500" $null
$supPageBal = $null
if ((CodeOf $pg) -eq '200' -and $null -ne $pg.data) {
  foreach ($r in @($pg.data.records)) { if ([int]$r.id -eq $SUP) { $supPageBal = $r.payableBalance } }
}
$ps = Api 'GET' "$BASE/finance/payment/payable-summary" $null
$sumUnpaid = $null
if ((CodeOf $ps) -eq '200' -and $null -ne $ps.data) {
  foreach ($r in @($ps.data)) { if ([int]$r.supplierId -eq $SUP) { $sumUnpaid = $r.unpaidAmount } }
}
if ($null -eq $supPageBal) { Bad ("supplier page did not return supplier $SUP (code=" + (CodeOf $pg) + " msg=" + (MsgOf $pg) + ")") }
else {
  Info "supplier page payableBalance=$supPageBal ; payable-summary unpaidAmount=$sumUnpaid"
  if ($null -ne $sumUnpaid -and (D $supPageBal) -eq (D $sumUnpaid)) { Ok "supplier page == payable summary ($supPageBal)" }
  else { Bad "supplier page ($supPageBal) != payable summary ($sumUnpaid)" }
  if ((D $supPageBal) -eq $supDbExcl) { Ok "supplier page == DB excluding transferred ($supDbExcl)" }
  else { Bad "supplier page ($supPageBal) != DB excluding transferred ($supDbExcl)" }
  if (($supDbExcl - $supDbIncl) -eq 250) { Ok "the transferred -250 offset is excluded (incl $supDbIncl -> excl $supDbExcl)" }
  else { Bad "unexpected transferred delta (" + ($supDbExcl - $supDbIncl) + ")" }
}
$ag = Api 'GET' "$BASE/finance/analysis/aging" $null
if ((CodeOf $ag) -ne '200') { Bad ("analysis/aging failed: " + (MsgOf $ag)) }
else {
  $p = $ag.data.payable
  $bucketSum = (D $p.notDue) + (D $p.d30) + (D $p.d60) + (D $p.d60p) + (D $p.none)
  if ((D $p.unpaid) -eq $allExcl) { Ok "analysis payable.unpaid == DB excl transferred ($allExcl)" }
  else { Bad "analysis payable.unpaid=" + $p.unpaid + " but DB excl transferred=$allExcl" }
  if ($bucketSum -eq $agingDb) { Ok "analysis payable buckets sum == DB aging subset ($agingDb)" }
  else { Bad "analysis buckets sum=$bucketSum but DB aging subset=$agingDb" }
  $topSum = [decimal]0
  foreach ($r in @($ag.data.topSuppliers)) { $topSum += (D $r.unpaid) }
  if ($topSum -eq $top5Db) { Ok "TOP5 suppliers payable sum == DB top5 ($top5Db)" }
  else { Bad "TOP5 sum=$topSum but DB top5=$top5Db" }
}

# ---------------- F7-36 ----------------
Write-Output '=== 2) F7-36 payment audit must respect the account balance ==='
$negRid = -1
$n1 = Api 'POST' "$BASE/finance/payment" (PyBody $PAY_SUP $ACC_POOR $PAYABLE $POOR_AMT)
if ((CodeOf $n1) -ne '200') { Bad ("overdraft draft create failed (unexpected): " + (MsgOf $n1)) }
else {
  $negRid = [int](SqlOne "SELECT id FROM finance_payment WHERE remark='VERIFY-F7F2' ORDER BY id DESC LIMIT 1")
  $na = Api 'PUT' "$BASE/finance/payment/$negRid/audit" $null
  $msg = MsgOf $na
  Write-Output ("  overdraft audit -> " + $msg)
  if (Rejected $na) { Ok ("overdraft rejected: " + $msg) } else { Bad 'overdraft was ACCEPTED (fix not effective)' }
  $st = SqlOne "SELECT status FROM finance_payment WHERE id=$negRid"
  $cf = SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE account_id=$ACC_POOR AND id>$maxCf"
  $payNow = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAYABLE"
  if ($st -eq 'DRAFT') { Ok "payment stays DRAFT (status=$st)" } else { Bad "payment status=$st (expect DRAFT)" }
  if ((D $cf) -eq 0) { Ok 'no cashflow was written' } else { Bad "$cf cashflow row(s) written" }
  if ($payNow -eq $pyA) { Ok "payable untouched ($payNow)" } else { Bad "payable changed: $pyA -> $payNow" }
}

Write-Output '--- positive control: healthy account 10 must audit, then roll back ---'
$okRid = -1
$p1 = Api 'POST' "$BASE/finance/payment" (PyBody $PAY_SUP $ACC_OK $PAYABLE $OK_AMT)
if ((CodeOf $p1) -ne '200') { Bad ("valid payment create failed: " + (MsgOf $p1)) }
else {
  $okRid = [int](SqlOne "SELECT id FROM finance_payment WHERE remark='VERIFY-F7F2' ORDER BY id DESC LIMIT 1")
  $pa = Api 'PUT' "$BASE/finance/payment/$okRid/audit" $null
  $payNow = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_payable WHERE id=$PAYABLE"
  $balOk1 = Bal $ACC_OK
  if ((CodeOf $pa) -eq '200' -and $payNow -eq '510.0000|1580.0000') { Ok "audit ok, payable $pyA -> $payNow" }
  else { Bad ("positive payment wrong: code=" + (CodeOf $pa) + " msg=" + (MsgOf $pa) + " payable=$payNow") }
  if ($balOk1 -eq ($balOk0 - $OK_AMT)) { Ok "account balance decreased by $OK_AMT ($balOk0 -> $balOk1)" }
  else { Bad "account balance $balOk0 -> $balOk1 (expect " + ($balOk0 - $OK_AMT) + ")" }
  $pu = Api 'PUT' "$BASE/finance/payment/$okRid/un-audit" $null
  # rollback is asserted on the MONEY only: the status regressing PARTIAL -> UNSETTLED is the
  # already-documented F7-35 (反审核重算台账状态缺 PARTIAL), reported separately below.
  $pyBack = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount) FROM finance_payable WHERE id=$PAYABLE"
  $pyMoney = "$($parts[0])|$($parts[1])"
  $pyBackStatus = SqlOne "SELECT status FROM finance_payable WHERE id=$PAYABLE"
  $balOk2 = Bal $ACC_OK
  if ((CodeOf $pu) -eq '200' -and $pyBack -eq $pyMoney) { Ok "un-audit restored the payable money ($pyBack)" }
  else { Bad ("un-audit problem: code=" + (CodeOf $pu) + " payable=$pyBack (expect $pyMoney)") }
  if ($pyBackStatus -eq $parts[2]) { Ok "un-audit restored the payable status ($pyBackStatus)" }
  else { Info "un-audit set status=$pyBackStatus (baseline $($parts[2])) -- known F7-35 defect (not fixed in this batch)" }
  if ($balOk2 -eq $balOk0) { Ok "un-audit restored the account balance ($balOk2)" }
  else { Bad "account balance not restored ($balOk0 -> $balOk2)" }
}

# ---------------- cleanup ----------------
Write-Output '=== cleanup ==='
Sql "UPDATE finance_payable SET paid_amount=$($parts[0]), unpaid_amount=$($parts[1]), status='$($parts[2])' WHERE id=$PAYABLE" | Out-Null
Sql "DELETE FROM finance_cashflow WHERE id > $maxCf" | Out-Null
Sql "DELETE FROM finance_settlement WHERE id > $maxSt" | Out-Null
Sql "DELETE FROM finance_payment_item WHERE id > $maxPyI" | Out-Null
Sql "DELETE FROM finance_payment WHERE id > $maxPy" | Out-Null

Write-Output '=== 9) post-check (must equal baseline) ==='
$pyFin = SqlOne "SELECT CONCAT(paid_amount,'|',unpaid_amount,'|',status) FROM finance_payable WHERE id=$PAYABLE"
$cPy = SqlOne 'SELECT COUNT(*) FROM finance_payment'; $cCf = SqlOne 'SELECT COUNT(*) FROM finance_cashflow'
$cSt = SqlOne 'SELECT COUNT(*) FROM finance_settlement'
$leak = SqlOne "SELECT COUNT(*) FROM finance_payment WHERE remark='VERIFY-F7F2'"
if ($pyFin -eq $pyA) { Ok "payable restored ($pyFin)" } else { Bad "payable drifted ($pyFin vs $pyA)" }
$bOk = Bal $ACC_OK; $bPoor = Bal $ACC_POOR
if ($bOk -eq $balOk0 -and $bPoor -eq $balPoor0) { Ok "account balances restored (ACC$ACC_OK=$bOk, ACC$ACC_POOR=$bPoor)" }
else { Bad "account balances drifted: ACC$ACC_OK=$bOk/$balOk0, ACC$ACC_POOR=$bPoor/$balPoor0" }
if ($cPy -eq $cPy0 -and $cCf -eq $cCf0 -and $cSt -eq $cSt0) { Ok "row counts restored (payment=$cPy cashflow=$cCf settlement=$cSt)" }
else { Bad "counts drifted (py=$cPy/$cPy0 cf=$cCf/$cCf0 st=$cSt/$cSt0)" }
if ($leak -eq '0') { Ok 'no probe document left behind' } else { Bad "$leak probe document(s) left behind" }

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
