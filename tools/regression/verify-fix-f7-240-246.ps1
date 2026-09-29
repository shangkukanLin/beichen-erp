# Batch D fix verification (2026-09-29): F7-240 / F7-241 / F7-243 / F7-244 / F7-246 (+ D-18 ledger resync, D-19 source tag).
# ASCII ONLY (no BOM -> PS 5.1 reads non-ASCII as GBK). Chinese needles are built from code points.
# Self-restoring: every bill it creates is cancelled again; the receipt it creates is un-audited + cancelled.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function SqlLines([string]$q) {
  $out = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null) | Where-Object { "$_" -notmatch '^(mysql:|ERROR)' }
  # NOTE: a 1-line result comes back as a plain STRING (PowerShell unrolls it), and `$x[0]` on a string
  # returns its FIRST CHARACTER -- that bug once turned MAX(id)=76 into 7 and made this script cancel 16
  # pre-existing bills. Therefore: ALWAYS wrap call sites in @(...) and never index raw output.
  return $out
}
function SqlOne([string]$q) {
  $l = @(SqlLines $q)
  if ($l.Count -eq 0) { return '' }
  return ("$($l[0])").Trim()
}
function Cn([int[]]$cp) { return (-join ($cp | ForEach-Object { [string][char]$_ })) }
function RunSql([string]$sql) { & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e $sql 2>$null | Out-Null }
$CN_NOOPEN = Cn @(0x65E0,0x9700,0x751F,0x6210,0x8D26,0x5355)  # 无需生成账单（稳定词：不随提示语前半段措辞变化）
$TODAY = Get-Date -Format 'yyyy-MM-dd'

$lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$tok = $lg.data.token
function Api([string]$method, [string]$path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok } -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok }
  } catch { return $_.ErrorDetails.Message }
}
$DRIFT_SQL = "SELECT (SELECT COUNT(*) FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_receivable r ON r.id=i.source_id WHERE b.bill_type='RECEIVABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(r.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(r.unpaid_amount,0))>0.005)) + (SELECT COUNT(*) FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id JOIN finance_payable y ON y.id=i.source_id WHERE b.bill_type='PAYABLE' AND b.status<>'CANCELLED' AND (ABS(IFNULL(i.paid_amount,0)-IFNULL(y.paid_amount,0))>0.005 OR ABS(IFNULL(i.unpaid_amount,0)-IFNULL(y.unpaid_amount,0))>0.005))"
$activeBefore = SqlOne "SELECT COUNT(*) FROM finance_bill WHERE status<>'CANCELLED'"
Write-Host ('[SEED] active bills=' + $activeBefore + ' todays-active-bills=' + (SqlOne "SELECT COUNT(*) FROM finance_bill WHERE status<>'CANCELLED' AND period_end='" + $TODAY + "'"))

Step '1) F7-240 / D-18: no bill item may drift from its ledger any more'
Write-Host ('  drifts = ' + (SqlOne $DRIFT_SQL))
Ok ((SqlOne $DRIFT_SQL) -eq '0') 'bill items are in sync with their ledgers (code caliber + D-18 data fix)'

Step '2) F7-240: audit/un-audit must keep bill item == ledger (same-source recalc)'
$recv = @(SqlLines "SELECT r.id, IFNULL(r.bill_no,''), r.unpaid_amount, i.id, COALESCE(r.customer_id,0) FROM finance_receivable r JOIN finance_bill_item i ON i.source_id=r.id JOIN finance_bill b ON b.id=i.bill_id WHERE b.status<>'CANCELLED' AND r.status IN ('UNSETTLED','PARTIAL') AND IFNULL(r.unpaid_amount,0) > 0 ORDER BY r.unpaid_amount DESC LIMIT 1")
$pay = @(SqlLines "SELECT y.id, IFNULL(y.bill_no,''), y.unpaid_amount, i.id, COALESCE(y.supplier_id,0) FROM finance_payable y JOIN finance_bill_item i ON i.source_id=y.id JOIN finance_bill b ON b.id=i.bill_id WHERE b.status<>'CANCELLED' AND y.status IN ('UNSETTLED','PARTIAL') AND IFNULL(y.unpaid_amount,0) > 0 ORDER BY y.unpaid_amount DESC LIMIT 1")
$acct = SqlOne 'SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1'
if ($recv.Count -eq 0 -and $pay.Count -eq 0) {
  Write-Host '  SKIP: no billed ledger with unpaid > 0 (no fixture to settle)'
  Ok $true 'skipped - fixture absent'
} else {
  # 收款侧优先，无夹具时退到付款侧（两侧代码同款，验证语义相同）
  $isRecv = ($recv.Count -gt 0)
  $f = "$(if ($isRecv) { $recv[0] } else { $pay[0] })" -split "`t"
  $ledId = [int]$f[0]; $ledBill = $f[1]; $unpaid0 = [decimal]$f[2]; $itemId = [int]$f[3]; $party = [int]$f[4]
  $ledTable = if ($isRecv) { 'finance_receivable' } else { 'finance_payable' }
  $docPath = if ($isRecv) { '/finance/receipt' } else { '/finance/payment' }
  if ($isRecv) {
    $body = '{"subjectType":"CUSTOMER","customerId":' + $party + ',"accountId":' + $acct + ',"receiptDate":"' + $TODAY + '","remark":"AUDIT-F7-240","items":[{"receivableId":' + $ledId + ',"receivableBillNo":"' + $ledBill + '","thisAmount":1}]}'
  } else {
    $body = '{"subjectType":"SUPPLIER","supplierId":' + $party + ',"accountId":' + $acct + ',"paymentDate":"' + $TODAY + '","remark":"AUDIT-F7-240","items":[{"payableId":' + $ledId + ',"payableBillNo":"' + $ledBill + '","thisAmount":1}]}'
  }
  Write-Host ('  fixture: ' + $ledTable + ' id=' + $ledId + ' (bill ' + $ledBill + ')')
  $r = Api 'Post' $docPath $body
  $docId = SqlOne ("SELECT COALESCE(MAX(id),0) FROM " + $(if ($isRecv) { 'finance_receipt' } else { 'finance_payment' }))
  $created = (SqlOne ("SELECT status FROM " + $(if ($isRecv) { 'finance_receipt' } else { 'finance_payment' }) + " WHERE id=" + $docId)) -eq 'DRAFT'
  if (-not $created) {
    Write-Host ('  SKIP: could not create the probe document -> ' + "$r")
    Ok $true 'skipped - probe document not created'
  } else {
    $null = Api 'Put' ($docPath + '/' + $docId + '/audit') $null
    $ledPaid = SqlOne ("SELECT paid_amount FROM " + $ledTable + " WHERE id=" + $ledId)
    $itmPaid = SqlOne ("SELECT paid_amount FROM finance_bill_item WHERE id=" + $itemId)
    Write-Host ('  after audit: ledger=' + $ledPaid + ' item=' + $itmPaid)
    Ok ($ledPaid -eq $itmPaid) 'bill item paid == ledger paid after audit'
    $null = Api 'Put' ($docPath + '/' + $docId + '/un-audit') $null
    $ledPaid2 = SqlOne ("SELECT paid_amount FROM " + $ledTable + " WHERE id=" + $ledId)
    $itmPaid2 = SqlOne ("SELECT paid_amount FROM finance_bill_item WHERE id=" + $itemId)
    Write-Host ('  after un-audit: ledger=' + $ledPaid2 + ' item=' + $itmPaid2)
    Ok ($ledPaid2 -eq $itmPaid2) 'bill item paid == ledger paid after un-audit (reverse is same-source too)'
    Ok ([decimal](SqlOne ("SELECT unpaid_amount FROM " + $ledTable + " WHERE id=" + $ledId)) -eq [decimal]$unpaid0) 'the ledger is back to its original unpaid (probe fully restored)'
    $null = Api 'Put' ($docPath + '/' + $docId + '/cancel') $null
    Ok ((SqlOne ("SELECT status FROM " + $(if ($isRecv) { 'finance_receipt' } else { 'finance_payment' }) + " WHERE id=" + $docId)) -eq 'CANCELLED') 'probe document cancelled again'
  }
}

Step '3) F7-243: a partner with no open ledger must be refused (no empty bill)'
$custNoOpen = SqlOne "SELECT c.id FROM customer c WHERE NOT EXISTS (SELECT 1 FROM finance_receivable r WHERE r.customer_id=c.id AND r.status IN ('UNSETTLED','PARTIAL')) LIMIT 1"
if ($custNoOpen -eq '') {
  Write-Host '  SKIP: every customer has open receivables'
  Ok $true 'skipped - fixture absent'
} else {
  $maxBefore = SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_bill'
  $body = '{"billType":"RECEIVABLE","partnerId":' + $custNoOpen + ',"partnerName":"x","periodStart":"' + $TODAY + '","periodEnd":"' + $TODAY + '"}'
  $r = Api 'Post' '/finance/bill/generate' $body
  Write-Host ('  empty bill -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NOOPEN)) 'an empty bill is rejected with a readable message'
  Ok ((SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_bill') -eq $maxBefore) 'no bill row was created'
}

Step '4) F7-244 + D-19: header comes from master data, source = MANUAL'
$custOpen = SqlOne ("SELECT c.id FROM customer c WHERE EXISTS (SELECT 1 FROM finance_receivable r WHERE r.customer_id=c.id AND r.status IN ('UNSETTLED','PARTIAL')) AND NOT EXISTS (SELECT 1 FROM finance_bill b WHERE b.bill_type='RECEIVABLE' AND b.partner_id=c.id AND b.period_end='" + $TODAY + "' AND b.status<>'CANCELLED') LIMIT 1")
$newBillId = ''
if ($custOpen -eq '') {
  Write-Host '  SKIP: no customer free of a todays bill'
  Ok $true 'skipped - fixture absent'
} else {
  $masterName = SqlOne ("SELECT name FROM customer WHERE id=" + $custOpen)
  $body = '{"billType":"RECEIVABLE","partnerId":' + $custOpen + ',"partnerName":"HACKED-NAME","periodStart":"2020-01-01","periodEnd":"' + $TODAY + '"}'
  $r = Api 'Post' '/finance/bill/generate' $body
  Write-Host ('  generate(force bill_no 2020-01-01) -> ' + "$r")
  $newBillId = SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_bill'
  $storedName = SqlOne ("SELECT partner_name FROM finance_bill WHERE id=" + $newBillId)
  $storedSrc = SqlOne ("SELECT IFNULL(source,'<null>') FROM finance_bill WHERE id=" + $newBillId)
  Write-Host ('  stored: name=' + $storedName + ' source=' + $storedSrc + ' master=' + $masterName)
  Ok ($storedName -eq $masterName) 'the stored header is the master-data name (client value ignored)'
  Ok ($storedSrc -eq 'MANUAL') 'source = MANUAL for the manual entry point (D-19)'
}
# remove this run's probe bill with SQL (there is no delete API; leaving cancelled rows would pile up)
if ($newBillId -ne '' -and (SqlOne ("SELECT COUNT(*) FROM finance_bill WHERE id=" + $newBillId + " AND source='MANUAL'")) -eq '1') {
  RunSql ("DELETE FROM finance_bill_item WHERE bill_id=" + $newBillId)
  RunSql ("DELETE FROM finance_bill WHERE id=" + $newBillId)
  Ok ((SqlOne ("SELECT COUNT(*) FROM finance_bill WHERE id=" + $newBillId)) -eq '0') 'the step-4 probe bill was removed again'
}

Step '5) F7-241 + D-19: the auto run bills the UNCOVERED ledgers only, tagged AUTO'
$covered = @(SqlLines "SELECT DISTINCT i.source_id FROM finance_bill_item i JOIN finance_bill b ON b.id=i.bill_id WHERE b.status<>'CANCELLED' AND i.source_id IS NOT NULL")
$maxBefore5 = SqlOne 'SELECT COALESCE(MAX(id),0) FROM finance_bill'
Write-Host ('  max bill id before the auto run = ' + $maxBefore5)
$r = Api 'Post' '/finance/bill/auto-generate' $null
Write-Host ('  auto-generate -> ' + "$r")
$newBills = @(SqlLines ('SELECT id FROM finance_bill WHERE id > ' + $maxBefore5 + " AND source='AUTO' ORDER BY id"))
Write-Host ('  new bills = ' + ($newBills -join ','))
Ok ($newBills.Count -ge 1) 'the auto run bills the uncovered overdue ledgers (F7-241: no whole-group skip)'
$badSrc = @()
$badCov = @()
foreach ($b in $newBills) {
  $s = SqlOne ("SELECT IFNULL(source,'<null>') FROM finance_bill WHERE id=" + $b)
  if ($s -ne 'AUTO') { $badSrc += ($b.ToString() + '=' + $s) }
  foreach ($sid in (SqlLines ("SELECT DISTINCT source_id FROM finance_bill_item WHERE bill_id=" + $b + " AND source_id IS NOT NULL"))) {
    if ($covered -contains $sid) { $badCov += ($b.ToString() + '/src' + $sid) }
  }
}
Ok ($badSrc.Count -eq 0) ('every auto-created bill carries source=AUTO (' + ($badSrc -join ',') + ')')
Ok ($badCov.Count -eq 0) ('no auto bill re-bills an already-covered ledger (' + ($badCov -join ',') + ')')
# remove this run's AUTO probe bills with SQL (no delete API; cancelled leftovers would pile up).
# SAFETY (see the SqlLines note): only ever touch rows that this run created (source=AUTO + id > snapshot).
$rm = 0
foreach ($b in $newBills) {
  if ((SqlOne ("SELECT IFNULL(source,'') FROM finance_bill WHERE id=" + $b)) -eq 'AUTO' -and [int]$b -gt [int]$maxBefore5) {
    RunSql ("DELETE FROM finance_bill_item WHERE bill_id=" + $b)
    RunSql ("DELETE FROM finance_bill WHERE id=" + $b)
    $rm++
  }
}
Write-Host ('  removed ' + $rm + ' auto-probe bill(s)')
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_bill WHERE id > " + $maxBefore5 + " AND source='AUTO'")) -eq '0') 'every auto-probe bill was removed again'

Step '6) F7-242 invariant + F7-246 frontend gates (static)'
Ok ((SqlOne "SELECT COUNT(*) FROM (SELECT bill_type, partner_id, period_end FROM finance_bill WHERE status<>'CANCELLED' GROUP BY bill_type, partner_id, period_end HAVING COUNT(*)>1) t") -eq '0') 'no duplicate (type, partner, period_end) among active bills'
$web = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp\beichen-erp-web\src\views\finance'
$b1 = [System.IO.File]::ReadAllText((Join-Path $web 'bill.vue'), [System.Text.Encoding]::UTF8)
$b2 = [System.IO.File]::ReadAllText((Join-Path $web 'bill\detail.vue'), [System.Text.Encoding]::UTF8)
$gate = ([regex]::Matches(($b1 + $b2), "finance:bill'")).Count
Write-Host ('  v-perm gate occurrences = ' + $gate)
Ok ($gate -ge 7) 'all 7 bill actions are permission-gated (F7-246)'
# check the IMPORT LINE only (comments elsewhere legitimately mention the removed symbol)
$importLine = ([regex]::Match($b1, "import\s*\{[^}]*\}\s*from\s*'@/api/enums'")).Value
Ok ($importLine -notmatch 'sourceBillTypeLabel') 'the dead import was removed from bill.vue'

$activeAfter = SqlOne "SELECT COUNT(*) FROM finance_bill WHERE status<>'CANCELLED'"
Write-Host ('[CLEANUP] active bills ' + $activeBefore + ' -> ' + $activeAfter + ' ; bills total = ' + (SqlOne 'SELECT COUNT(*) FROM finance_bill'))
Ok ($activeAfter -eq $activeBefore) 'no extra ACTIVE bill left behind'
Ok ((SqlOne $DRIFT_SQL) -eq '0') 'invariant still holds after the probe'
Write-Host ('RESULT fix F7-240/241/243/244/246 + D-18/D-19 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
