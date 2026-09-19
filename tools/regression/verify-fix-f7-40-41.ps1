# verify-fix-f7-40-41.ps1  (regression for the F7-40 / F7-41 fixes)  -- READ-ONLY, no data is written.
#
# F7-40: "应收未收 / 应付未付" had three different rules. xxxSummary used `status != CANCELLED`,
#        which also counted the ADVANCE ledgers (negative prepay rows), so the home KPI showed a
#        NEGATIVE receivable (-4,260) and disagreed with the aging buckets on the same page
#        (which use UNSETTLED/PARTIAL). Fix: `unpaid` now only counts UNSETTLED/PARTIAL, while
#        `total`/`paid` stay all-non-cancelled (they are the collection-rate numerator/denominator).
# F7-41: the receipt write-off dropdown (ReceivableQuery.unpaid) lacked the I11 filter that the
#        payment side has (amount > 0), so it offered ADVANCE prepay rows (100% failure at audit)
#        and negative UNSETTLED receivables (which create ANOTHER prepay row when settled).
#
# Both findings were proved live before the fix (see report 7.3.1 / 7.3.2); this script asserts the
# post-fix invariants plus POSITIVE CONTROLS (collection-rate inputs unchanged; a customer with a
# real unpaid receivable still gets dropdown rows). ASCII-only on purpose.

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$BASE  = 'http://localhost:8080/api'
$CUST_EMPTY = 24     # had ONLY advance/negative rows -> dropdown must now be empty
$SUP_PAY    = 26     # payable dropdown control

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

$body = '{"username":"lin","password":"123","companyId":1}'
try {
  $login = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes($body))
} catch { Write-Output ("LOGIN EX " + $_.Exception.Message); exit 1 }
$H = @{ Authorization = [string]$login.data.token }
function Api([string]$url) {
  try { return Invoke-RestMethod -Uri $url -Method Get -Headers $H }
  catch { return [pscustomobject]@{ code = -1; msg = "HTTPEX " + $_.Exception.Message; data = $null } }
}

# ---------------- baseline (DB truth, same rules as the fixed SQL) ----------------
Write-Output '=== 0) baseline (DB 口径真值) ==='
$dbRcUnpaid = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL')")
$dbRcTotal  = D (SqlOne "SELECT IFNULL(SUM(amount),0) FROM finance_receivable WHERE status<>'CANCELLED'")
$dbRcPaid   = D (SqlOne "SELECT IFNULL(SUM(paid_amount),0) FROM finance_receivable WHERE status<>'CANCELLED'")
$dbPyUnpaid = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1")
$dbPyTotal  = D (SqlOne "SELECT IFNULL(SUM(amount),0) FROM finance_payable WHERE status<>'CANCELLED' AND IFNULL(transferred_to_receivable,0)<>1")
$dbPyPaid   = D (SqlOne "SELECT IFNULL(SUM(paid_amount),0) FROM finance_payable WHERE status<>'CANCELLED' AND IFNULL(transferred_to_receivable,0)<>1")
$advRc = SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='ADVANCE'"
$advPy = SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='ADVANCE'"
$cRc = SqlOne 'SELECT COUNT(*) FROM finance_receivable'; $cPy = SqlOne 'SELECT COUNT(*) FROM finance_payable'
Info "DB 应收未收(UNSETTLED/PARTIAL)=$dbRcUnpaid  total=$dbRcTotal paid=$dbRcPaid"
Info "DB 应付未付(UNSETTLED/PARTIAL,excl transferred)=$dbPyUnpaid  total=$dbPyTotal paid=$dbPyPaid"
Info "advance rows: receivable=$advRc payable=$advPy ; row counts: rc=$cRc py=$cPy"

# ---------------- F7-40 ----------------
Write-Output '=== 1) F7-40 首页/账龄/分桶三处口径一致（且不再为负） ==='
$sm = Api "$BASE/finance/analysis/summary"
$ag = Api "$BASE/finance/analysis/aging"
if ($null -eq $sm.data -or $null -eq $ag.data) { Bad "analysis endpoints unavailable (summary.code=$(if($sm){$sm.code}) aging.code=$(if($ag){$ag.code}))" } else {
  $rcU = D $ag.data.receivable.unpaid
  $rcBuckets = (D $ag.data.receivable.notDue) + (D $ag.data.receivable.d30) + (D $ag.data.receivable.d60) + (D $ag.data.receivable.d60p) + (D $ag.data.receivable.none)
  $pyU = D $ag.data.payable.unpaid
  $pyBuckets = (D $ag.data.payable.notDue) + (D $ag.data.payable.d30) + (D $ag.data.payable.d60) + (D $ag.data.payable.d60p) + (D $ag.data.payable.none)
  $healthRc = D $sm.data.health.receivableUnpaid
  $healthPy = D $sm.data.health.payableUnpaid
  Info "应收: aging.unpaid=$rcU  分桶合计=$rcBuckets  首页KPI=$healthRc"
  Info "应付: aging.unpaid=$pyU  分桶合计=$pyBuckets  首页KPI=$healthPy"
  if ($rcU -eq $rcBuckets) { Ok "应收 aging.unpaid == 分桶合计 ($rcBuckets)" } else { Bad "应收同页不一致: unpaid=$rcU vs 分桶=$rcBuckets" }
  if ($pyU -eq $pyBuckets) { Ok "应付 aging.unpaid == 分桶合计 ($pyBuckets)" } else { Bad "应付同页不一致: unpaid=$pyU vs 分桶=$pyBuckets" }
  if ($healthRc -eq $rcU -and $healthPy -eq $pyU) { Ok "首页 KPI == 账龄页 ($healthRc / $healthPy)" } else { Bad "首页与账龄不一致: $healthRc/$rcU , $healthPy/$pyU" }
  if ($rcU -eq $dbRcUnpaid -and $pyU -eq $dbPyUnpaid) { Ok "账龄口径 == DB UNSETTLED/PARTIAL ($dbRcUnpaid / $dbPyUnpaid)" } else { Bad "与 DB 不一致: rc=$rcU/$dbRcUnpaid py=$pyU/$dbPyUnpaid" }
  if ($rcU -ge 0 -and $pyU -ge 0) { Ok "未收/未付均非负（预收/预付负台账已剔除）" } else { Bad "仍为负: rc=$rcU py=$pyU" }
  # positive control: collection/payment rate inputs must stay all-non-cancelled
  $rcT = D $ag.data.receivable.total; $rcP = D $ag.data.receivable.paid
  $pyT = D $ag.data.payable.total;    $pyP = D $ag.data.payable.paid
  if ($rcT -eq $dbRcTotal -and $rcP -eq $dbRcPaid) { Ok "应收 total/paid 仍为全量口径（回款率分子分母未受影响）: $rcT/$rcP" }
  else { Bad "回款率口径被误改: total=$rcT/$dbRcTotal paid=$rcP/$dbRcPaid" }
  if ($pyT -eq $dbPyTotal -and $pyP -eq $dbPyPaid) { Ok "应付 total/paid 仍为全量口径（付款率未受影响）: $pyT/$pyP" }
  else { Bad "付款率口径被误改: total=$pyT/$dbPyTotal paid=$pyP/$dbPyPaid" }
}
$sb = Api "$BASE/finance/analysis/subject"
if ($null -ne $sb.data) {
  $byType = [decimal]0
  foreach ($v in $sb.data.payableByType.PSObject.Properties) { $byType += (D $v.Value) }
  $neg = @()
  foreach ($v in $sb.data.payableByType.PSObject.Properties) { if ((D $v.Value) -lt 0) { $neg += ($v.Name + "=" + $v.Value) } }
  Info ("subject().payableByType 合计=$byType ; 负值项=" + $(if ($neg.Count -eq 0) { '无' } else { ($neg -join ',') }))
  if ($byType -eq $dbPyUnpaid) { Ok "subject().payableByType 合计 == 应付未付 ($dbPyUnpaid)" } else { Bad "subject() 合计 $byType != $dbPyUnpaid" }
  if ($neg.Count -eq 0) { Ok "subject() 不再出现负的应付类型（原 other=-2,090 已剔除）" } else { Bad ("仍含负值: " + ($neg -join ',')) }
} else { Bad "subject 接口不可用" }

Write-Output '--- 供应商页余额 / 付款页汇总：per-supplier 口径也必须排除预付台账 ---'
$supAdv = [int](SqlOne "SELECT supplier_id FROM finance_payable WHERE status='ADVANCE' GROUP BY supplier_id ORDER BY supplier_id LIMIT 1")
$dbNew = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE supplier_id=$supAdv AND status IN ('UNSETTLED','PARTIAL') AND IFNULL(transferred_to_receivable,0)<>1")
$dbOld = D (SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_payable WHERE supplier_id=$supAdv AND status<>'CANCELLED' AND IFNULL(transferred_to_receivable,0)<>1")
$ps2 = Api "$BASE/finance/payment/payable-summary"
$pg2 = Api "$BASE/supplier/page?pageNum=1&pageSize=500"
$sumVal = $null; $pageVal = $null
if ($null -ne $ps2.data) { foreach ($r in @($ps2.data)) { if ([int]$r.supplierId -eq $supAdv) { $sumVal = $r.unpaidAmount } } }
if ($null -ne $pg2.data) { foreach ($r in @($pg2.data.records)) { if ([int]$r.id -eq $supAdv) { $pageVal = $r.payableBalance } } }
Info "供应商 $supAdv：DB 新口径=$dbNew  旧口径(含预付)=$dbOld  付款页汇总=$sumVal  供应商页余额=$pageVal"
if ($null -ne $sumVal -and (D $sumVal) -eq $dbNew) { Ok "付款页 per-supplier 未付 == DB 新口径 ($dbNew)" } else { Bad "付款页 per-supplier: $sumVal != $dbNew" }
if ($null -ne $pageVal -and (D $pageVal) -eq $dbNew) { Ok "供应商页余额 == DB 新口径 ($dbNew)" } else { Bad "供应商页余额: $pageVal != $dbNew" }
if ($dbNew -ne $dbOld) { Ok "该供应商确有预付台账被剔除（新 $dbNew vs 旧 $dbOld）" } else { Info "该供应商无预付台账差异（此项不构成实证）" }

# ---------------- F7-41 ----------------
Write-Output '=== 2) F7-41 收款核销下拉只列正数未结清应收 ==='
function CheckRows([string]$label, $rows) {
  $bad = @()
  foreach ($r in @($rows)) {
    $st = "$($r.status)"
    $amt = D $r.amount
    if ($st -eq 'ADVANCE') { $bad += ("id=$($r.id) ADVANCE") }
    elseif ($amt -le 0) { $bad += ("id=$($r.id) amount=$amt") }
    elseif ($st -ne 'UNSETTLED' -and $st -ne 'PARTIAL') { $bad += ("id=$($r.id) status=$st") }
  }
  if ($bad.Count -eq 0) { Ok "${label}: 全部合法（$(@($rows).Count) 行，无 ADVANCE / 无非正数 / 状态仅 UNSETTLED|PARTIAL）" }
  else { Bad ("${label}: 含非法行 -> " + ($bad -join ' ; ')) }
}
$d1 = Api "$BASE/finance/receipt/unpaid-receivables?customerId=$CUST_EMPTY"
if ($null -ne $d1.data) { CheckRows "客户 $CUST_EMPTY 收款下拉" $d1.data } else { Bad "收款下拉接口不可用" }
$custOk = [int](SqlOne "SELECT customer_id FROM finance_receivable WHERE status='UNSETTLED' AND amount>0 AND unpaid_amount>0 AND customer_id IS NOT NULL AND subject_type='CUSTOMER' ORDER BY id LIMIT 1")
$d2 = Api "$BASE/finance/receipt/unpaid-receivables?customerId=$custOk"
if ($null -ne $d2.data) {
  CheckRows "客户 $custOk（有真实未结清应收）收款下拉" $d2.data
  if (@($d2.data).Count -ge 1) { Ok "正例对照：真实未结清应收仍可选择（$(@($d2.data).Count) 行）" } else { Bad "过度过滤：客户 $custOk 的未结清应收被清空" }
} else { Bad "收款下拉接口不可用（正例）" }
$d3 = Api "$BASE/finance/payment/unpaid-payables?supplierId=$SUP_PAY"
if ($null -ne $d3.data) { CheckRows "供应商 $SUP_PAY 付款下拉" $d3.data } else { Bad "付款下拉接口不可用" }

# ---------------- 收尾（纯读，无写入） ----------------
Write-Output '=== 9) 收尾自检（本批只读，数据必须零变化） ==='
if ((SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE status='ADVANCE'") -eq $advRc -and
    (SqlOne "SELECT COUNT(*) FROM finance_payable WHERE status='ADVANCE'") -eq $advPy -and
    (SqlOne 'SELECT COUNT(*) FROM finance_receivable') -eq $cRc -and
    (SqlOne 'SELECT COUNT(*) FROM finance_payable') -eq $cPy) { Ok '应收/应付行数与 ADVANCE 行数均未变化（零写入）' }
else { Bad '行数发生变化（本批不应有任何写入）' }

Write-Output ("RESULT " + $(if ($script:fails -eq 0) { 'PASS' } else { "FAIL($script:fails)" }))
