# =====================================================================================
# Batch F + G fix verification (2026-09-30) — F7-250/251/252/253/254 + F7-255/256/257/258/259/260
# Read-only except: (a) the cleanup script sections 9/10 are applied with -Apply (backup first),
# (b) nothing else writes. Prints PASS/FAIL per assertion + a final RESULT line.
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-fix-f7-250-260.ps1
# =====================================================================================
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$erp = Join-Path $repo 'beichen-erp'
$base = 'http://localhost:8080/api'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function SqlOne([string]$sql) {
  $l = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
    Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
  return ("$l").Trim()
}
function SqlAll([string]$sql) {
  @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
    Where-Object { "$_" -notmatch '^(mysql:|ERROR)' }
}
function Lg($u) {
  try { return (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
      -Body ('{"username":"' + $u + '","password":"123","companyId":1}')).data.token } catch { return $null }
}
function ApiCode($tok, $path) {
  try { $r = Invoke-RestMethod -Uri ($base + $path) -Headers @{ Authorization = $tok }; return [int]$r.code }
  catch { return [int]$_.Exception.Response.StatusCode.value__ }
}

Write-Host '=== batch F/G fix verification (F7-250..260) ==='

Sec '0) data corrections: cleanup script sections 9 + 10 (backup kept)'
$o = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $erp 'tools\regression\audit\audit-20260929-fin-cleanup-fixtures.ps1') -Apply -Sections 9,10 2>&1
$o | Select-String -Pattern 'backup written|cancelled \(F7-250\)|tagged \(F7-254\)' | ForEach-Object { Write-Host ('    ' + $_.Line.Trim()) }

Sec '1) F7-250: no LIVE 0-yuan claim receivable from outsource return-backs'
$z = SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE source_bill_type='OUTSOURCE_RETURN_BACK' AND status<>'CANCELLED' AND IFNULL(amount,0)=0"
Write-Host ('    active 0-yuan rows = ' + $z)
Ok ("$z" -eq '0') 'F7-250 assertion holds (code guard + data correction)'

Sec '2) F7-252: claim/loss upsert now refuses to reuse a row that already has payments'
Write-Host '    guard present in source:'
$hit252 = Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\finance\service\StockLossAccountingHelper.java') -Pattern '已有收款记录，不可重建来源台账'
Ok ([bool]$hit252) 'upsertClaimReceivable rejects reuse of an already-paid claim receivable'
$hit250b = Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\finance\service\StockLossAccountingHelper.java') -Pattern '金额 ≤ 0 不落账'
Ok ([bool]$hit250b) 'upsertLossExpense skips amount<=0 (symmetry with F7-250)'
$hit250 = Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\outsource\service\impl\OutsourceReturnBackServiceImpl.java') -Pattern '金额 ≤ 0 不落账'
Ok ([bool]$hit250) 'outsource return-back upsertReceivable skips amount<=0'

Sec '3) F7-253: repair-charge source_id granularity documented (mixed by design)'
$hit253 = Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\outsource\service\impl\OutsourceReturnOrderServiceImpl.java') -Pattern 'F7-253'
Ok ([bool]$hit253) 'F7-253 note present (whole-order vs per-row source_id)'

Sec '4) F7-254: pre-fix stock-loss bills are tagged (no fabricated vouchers)'
$t = SqlOne "SELECT COUNT(*) FROM inventory_stock_loss WHERE id BETWEEN 8 AND 21 AND status='AUDITED' AND remark LIKE '%F7-254%'"
Write-Host ('    tagged bills = ' + $t)
Ok ([int]$t -gt 0) 'historical stock-loss bills tagged for follow-up'

Sec '5) F7-251: frontend action buttons in outsource/inventory are permission-gated'
$vp = (Get-ChildItem (Join-Path $erp 'beichen-erp-web\src\views\outsource'), (Join-Path $erp 'beichen-erp-web\src\views\inventory') -Recurse -Filter *.vue |
  Select-String -Pattern 'v-perm' | Measure-Object).Count
Write-Host ('    v-perm hits in outsource+inventory = ' + $vp)
Ok ([int]$vp -ge 70) 'dangerous actions carry v-perm (was 0)'
$bad = (Get-ChildItem (Join-Path $erp 'beichen-erp-web\src\views\outsource'), (Join-Path $erp 'beichen-erp-web\src\views\inventory') -Recurse -Filter *.vue |
  Select-String -Pattern "v-perm='" | Measure-Object).Count
Ok ([int]$bad -eq 0) 'all v-perm values use the project style v-perm="''code''" (TS-clean)'

Sec '6) F7-255: analysis endpoints code-gated (positive + negative)'
$admin = Lg 'lin'
$low = Lg 'perm_test'
Ok ([bool]$admin) 'admin (lin) login ok'
Ok ([bool]$low) 'low-privilege (perm_test) login ok'
foreach ($p in @('/finance/analysis/summary', '/finance/analysis/profit', '/finance/analysis/aging', '/finance/analysis/tax', '/sale/analysis', '/customer/analysis')) {
  $a = ApiCode $admin $p; $l = ApiCode $low $p
  Write-Host ('    ' + $p + '  admin=' + $a + '  low=' + $l)
  Ok ($a -eq 200) ('admin can read ' + $p)
  Ok ($l -eq 403) ('low-privilege is DENIED ' + $p)
}
$b1 = ApiCode $low '/finance/bill/page?pageNum=1&pageSize=1'
Ok ($b1 -eq 403) 'control: low-privilege still denied on /finance/bill/page'

Sec '7) F7-256: analysis aging/summary now uses the ledger-page caliber (positive rows only)'
$api = Invoke-RestMethod -Uri ($base + '/finance/analysis/aging') -Headers @{ Authorization = $admin }
$apiD30 = [string]([decimal]$api.data.receivable.d30)
$apiUnpaid = [string]([decimal]$api.data.receivable.unpaid)
$sqlD30 = [string]([decimal](SqlOne "SELECT IFNULL(SUM(unpaid_amount),0) FROM finance_receivable WHERE status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)>0 AND due_date IS NOT NULL AND due_date<CURDATE()"))
$sqlUnpaid = [string]([decimal](SqlOne "SELECT IFNULL(SUM(CASE WHEN status IN ('UNSETTLED','PARTIAL') AND IFNULL(amount,0)>0 THEN unpaid_amount ELSE 0 END),0) FROM finance_receivable WHERE status<>'CANCELLED'"))
Write-Host ('    d30 api=' + $apiD30 + ' sql=' + $sqlD30 + ' | unpaid api=' + $apiUnpaid + ' sql=' + $sqlUnpaid)
Ok ("$apiD30" -eq "$sqlD30") 'aging bucket matches positive-row-only SQL'
Ok ("$apiUnpaid" -eq "$sqlUnpaid") 'summary unpaid matches positive-row-only SQL'

Sec '8) F7-257: self-check ran clean (no unregistered endpoint, no EXEMPT write)'
$lg = Join-Path $erp 'beichen-erp-server\logs\beichen-erp.log'
# Only the LATEST self-check block matters (older blocks legitimately contain the pre-fix complaints):
# everything AFTER the most recent "收口一致性 OK" line must contain no complaint.
$sc = @(Select-String -Path $lg -Pattern 'perm-selfcheck')
$okIdx = -1
for ($i = 0; $i -lt $sc.Count; $i++) { if ($sc[$i].Line -match '收口一致性 OK') { $okIdx = $i } }
Ok ($okIdx -ge 0) 'perm-selfcheck reported 收口一致性 OK at least once'
$after = if ($okIdx -ge 0) { $sc[($okIdx + 1)..($sc.Count - 1)] } else { @() }
Ok (-not [bool]($after | Where-Object { $_.Line -match '未收口 ->|EXEMPT 白名单里出现' })) 'no unregistered/EXEMPT-write entries after the latest OK block'
$oc = SqlOne "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name='finance_account' AND index_name='uk_account_name'"
Ok ($oc -eq '1') 'uk_account_name index present'

Sec '9) F7-258: every finance unique key carries company_id'
foreach ($row in SqlAll "SELECT CONCAT(table_name,'/',index_name,' => ',GROUP_CONCAT(column_name ORDER BY seq_in_index)) FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name LIKE 'finance\_%' AND non_unique=0 AND index_name<>'PRIMARY' GROUP BY table_name, index_name ORDER BY table_name") {
  Write-Host ('    ' + $row)
}
$noCid = SqlOne "SELECT COUNT(*) FROM (SELECT table_name, index_name FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name LIKE 'finance\_%' AND non_unique=0 AND index_name<>'PRIMARY' GROUP BY table_name, index_name HAVING SUM(CASE WHEN column_name='company_id' THEN 1 ELSE 0 END)=0) x"
Ok ($noCid -eq '0') 'all finance unique keys include company_id'

Sec '10) F7-259/260: frontend fixes in place'
$dash = Get-Content -LiteralPath (Join-Path $erp 'beichen-erp-web\src\views\dashboard\index.vue') -Encoding UTF8 -Raw
Ok ($dash -match 'fetchAllRecords') 'dashboard fetches product/stock rows page-by-page (no silent 200-row cut)'
Ok (-not ($dash -match "request\.get<any, any>\('/product/page', \{ params: \{ pageSize: 200 \}")) 'no fixed pageSize:200 on /product/page'
$fmt = Test-Path (Join-Path $erp 'beichen-erp-web\src\utils\format.ts')
Ok $fmt 'shared money/percent util exists (utils/format.ts)'

Sec '11) D-25 wrap-up: conditional v-perm + historical LOSS vouchers + /profit retention'
$idxFile = Join-Path $erp 'beichen-erp-web\src\views\outsource\return-order\index.vue'
$detFile = Join-Path $erp 'beichen-erp-web\src\views\outsource\return-order\detail.vue'
$idx = Get-Content -LiteralPath $idxFile -Encoding UTF8 -Raw
$det = Get-Content -LiteralPath $detFile -Encoding UTF8 -Raw
Ok ($idx -match 'v-perm="actionPerm"') 'index.vue gates both leaves through the leaf-aware actionPerm'
Ok ($idx -match 'actionPerm = computed') 'actionPerm defined in script (avoids vue-tsc narrowing TS2367)'
Ok ($det -match 'v-perm="''outsource:return-order''"') 'detail.vue gates its actions with the page code (expression form)'
$bf = SqlOne "SELECT CONCAT(COUNT(*),'/',IFNULL(SUM(amount),0)) FROM finance_expense WHERE source_bill_type='INVENTORY_STOCK_LOSS' AND source_id BETWEEN 8 AND 21"
Write-Host ('    backfilled LOSS vouchers (count/sum) = ' + $bf)
Ok ("$bf" -eq '8/125.2100') 'historical LOSS vouchers created for the audited bills (8 / 125.21)'
$zero = SqlOne "SELECT COUNT(*) FROM finance_expense e JOIN inventory_stock_loss s ON s.id = e.source_id WHERE e.source_bill_type='INVENTORY_STOCK_LOSS' AND IFNULL(s.total_amount,0) = 0"
Ok ("$zero" -eq '0') 'no voucher fabricated for 0-amount bills (mirrors the F7-250 guard)'
$doc = Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\finance\controller\FinanceAnalysisController.java') -Pattern 'D-25'
Ok ([bool]$doc) '/profit endpoint documented as retained (page retired)'
$tmp = Test-Path (Join-Path $erp 'tools\regression\audit\tmp-patch-return-order-perm.ps1')
Ok (-not $tmp) 'temporary patch script removed'

Sec '12) D-26 wrap-up: F7-261 generator retry + F7-263 legacy numbers'
$srvPath = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp'
$remain = @(Get-ChildItem $srvPath -Recurse -Filter *.java | Select-String -Pattern 'BillNoSeq\.format\(')
Ok ($remain.Count -eq 0) 'no BillNoSeq.format( call site left (all go through formatUnique)'
$uniq = @(Get-ChildItem $srvPath -Recurse -Filter *.java | Select-String -Pattern 'BillNoSeq\.formatUnique\(')
Write-Host ('    formatUnique sites = ' + $uniq.Count)
Ok ($uniq.Count -ge 17) 'all 17 generation sites are collision-aware'
$util = Get-Content -LiteralPath (Join-Path $srvPath 'common\BillNoSeq.java') -Encoding UTF8 -Raw
Ok ($util -match 'public static String formatUnique') 'BillNoSeq.formatUnique exists (central retry)'
$tag = SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE bill_no LIKE '%-ADVANCE-ADVANCE%' AND remark LIKE '%F7-263%'"
Write-Host ('    F7-263 tagged rows = ' + $tag)
Ok ("$tag" -eq '3') 'F7-263 legacy numbers tagged (3 rows)'
$doubled = SqlOne "SELECT COUNT(*) FROM finance_receivable WHERE bill_no LIKE '%-ADVANCE-ADVANCE%'"
Ok ("$doubled" -eq '3') 'F7-263 numbers left untouched (renumber would collide with uk_bill_no)'

Write-Host ''
Write-Host ('RESULT  PASS=' + $pass + '  FAIL=' + $fail)
if ($fail -gt 0) { exit 1 } else { exit 0 }
