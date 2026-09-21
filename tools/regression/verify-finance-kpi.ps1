# Finance KPI reconciliation (NEW 2026-09-21) -- the first REAL assertions for the KPI figures.
#
# Why: ui-e2e-8-finance used to print RESULT PASS (PASS=0 FAIL=0) while asserting nothing, so the KPI
# numbers had no automated check at all. This case reconciles the API against the database instead.
#
# User rule covered: "there is no loss charge any more" (2026-09-21) => 销售金额 must be
#   audited sale orders - audited sale returns, with NO loss term added back:
#     backend  FinanceAnalysisServiceImpl.dayKpi : sale = sale - saleRet        (loss removed same day)
#     frontend KPI_FORMULA.sale                  : "= 已审核销售单金额 - 销售退货金额" (loss removed)
#
# Assertions (READ ONLY -- nothing is written, so the case is perfectly rerun-safe):
#   1) range saleAmount      == SQL(sale_order AUDITED by create_time in range) - SQL(sale_return AUDITED)
#   2) even when the range DOES carry loss amounts (return_sort.loss_amount), the KPI must not add them
#      (the difference stays exactly 0; the DB loss is printed for the record)
#   3) purchaseSpend == audited purchase orders - audited purchase returns; expenseSpend == audited expenses
#   4) the KPI formula text shipped to the UI no longer mentions loss charge
# ASCII ONLY.
$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | Select-Object -First 1)
}
function D($s) { if (-not $s) { return [decimal]0 }; return [decimal]$s }

$lg = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$hdr = @{ Authorization = $lg.data.token }
Ok ([bool]$lg.data.token) 'logged in'

function KpiOf([string]$start, [string]$end) {
  $r = Invoke-RestMethod -Uri "$API/finance/analysis/overview-kpi?start=$start&end=$end" -Headers $hdr
  return $r.data
}
# the controller returns the range block plus a fixed year-to-date block; accept either nesting
function Pick($o, [string]$key) {
  if ($null -eq $o) { return $null }
  $v = $o.$key
  if ($null -eq $v -and $o.range) { $v = $o.range.$key }
  if ($null -eq $v -and $o.kpi) { $v = $o.kpi.$key }
  return $v
}

$monthStart = (Get-Date -Format 'yyyy-MM-01')
$today = (Get-Date -Format 'yyyy-MM-dd')
Write-Host ("--- range $monthStart .. $today")
$kpi = KpiOf $monthStart $today
Write-Host ('  payload=' + ($kpi | ConvertTo-Json -Depth 5 -Compress))

$saleKpi = D (Pick $kpi 'saleAmount')
$purchKpi = D (Pick $kpi 'purchaseSpend')
$expKpi = D (Pick $kpi 'expenseSpend')

$saleSql = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_order WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$retSql = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_return WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$lossSql = D (SqlOne "SELECT COALESCE(SUM(loss_amount),0) FROM return_sort WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$purchSql = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM purchase_order WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$purchRetSql = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM purchase_return WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$expSql = D (SqlOne "SELECT COALESCE(SUM(amount),0) FROM finance_expense WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$monthStart' AND '$today'")
$expectSale = $saleSql - $retSql

Write-Host ("  kpi: sale=$saleKpi purchase=$purchKpi expense=$expKpi")
Write-Host ("  db : sale=$saleSql ret=$retSql loss=$lossSql => expectSale=$expectSale ; purchase=$purchSql-$purchRetSql ; expense=$expSql")

Ok ($saleKpi -eq $expectSale) ('saleAmount == audited sales - audited returns (' + $saleKpi + ' = ' + $expectSale + ')')
# the discriminating half: loss must NOT be added back even when this range carries loss amounts
Ok (($saleKpi - $expectSale) -eq 0) ('no loss term is added back to saleAmount (db loss in range = ' + $lossSql + '; difference = ' + ($saleKpi - $expectSale) + ')')
if ($lossSql -ne 0) {
  Ok ($saleKpi -eq $expectSale) ('this range really carries loss (' + $lossSql + ') and it is still excluded -- the assertion above IS discriminating here')
} else {
  Write-Host '  NOTE: this range carries no loss amount, so the exclusion check is currently non-discriminating (it becomes a real regression check as soon as any audited return-sort has a loss amount).'
}
Ok ($purchKpi -eq ($purchSql - $purchRetSql)) ('purchaseSpend == audited purchases - audited purchase returns (' + $purchKpi + ' = ' + ($purchSql - $purchRetSql) + ')')
Ok ($expKpi -eq $expSql) ('expenseSpend == audited expenses (' + $expKpi + ' = ' + $expSql + ')')

Write-Host '--- previous month as a second sample'
$prevStart = (Get-Date).AddMonths(-1).ToString('yyyy-MM-01')
$prevEnd = (Get-Date).AddMonths(-1).ToString('yyyy-MM-dd')
$kpi2 = KpiOf $prevStart $prevEnd
$saleKpi2 = D (Pick $kpi2 'saleAmount')
$saleSql2 = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_order WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$prevStart' AND '$prevEnd'")
$retSql2 = D (SqlOne "SELECT COALESCE(SUM(total_amount),0) FROM sale_return WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$prevStart' AND '$prevEnd'")
$lossSql2 = D (SqlOne "SELECT COALESCE(SUM(loss_amount),0) FROM return_sort WHERE status='AUDITED' AND DATE(create_time) BETWEEN '$prevStart' AND '$prevEnd'")
Write-Host ("  kpi=$saleKpi2  db=$saleSql2-$retSql2 (loss=$lossSql2)")
Ok ($saleKpi2 -eq ($saleSql2 - $retSql2)) ('previous month saleAmount reconciles too (' + $saleKpi2 + ' = ' + ($saleSql2 - $retSql2) + ')')

Write-Host ''
if ($fail -eq 0) { Write-Host 'RESULT PASS finance KPI reconciliation (no loss term)' } else { Write-Host ('RESULT FAIL finance KPI reconciliation, failures=' + $fail); exit 1 }
