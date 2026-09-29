# =====================================================================================
# Settings-batch D fix verification (2026-09-30) — F8-16 / F8-17 / F8-18 / F8-19 / F8-05
#
# ********** 安全红线：本脚本**永远不会**带着 confirm 调用清空接口 **********
# 允许的调用只有两种：
#   (a) POST /api/system/clear-company-data  **不带 confirm**      -> 期望被拒绝（早返回，零副作用）
#   (b) POST /api/system/clear-company-data?dryRun=true            -> 只读 + 生成回滚点 + 报将删行数
# 断言里还会对比清空前后的行数，确保"什么都没被删"。
#
# 唯一写库动作：清理脚本第 14 段（菜单 908 文案，先备份）；以及 dryRun 产生的 sys_operation_log 审计行。
# Usage:  powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-fix-f8-16-19.ps1
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
function ApiPost($tok, $path, $body) {
  try {
    $p = @{ Uri = ($base + $path); Method = 'Post'; Headers = @{ Authorization = $tok }; ContentType = 'application/json' }
    if ($body -ne $null) { $p.Body = $body }
    return Invoke-RestMethod @p
  } catch {
    return @{ code = [int]$_.Exception.Response.StatusCode.value__; msg = 'HTTP ' + [int]$_.Exception.Response.StatusCode.value__ }
  }
}

Write-Host '=== settings batch D fix verification (F8-16..F8-19, F8-05) ==='

$cc = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\ClearController.java'
$sc = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\ClearTableSelfCheck.java'
$dm = Join-Path $erp 'beichen-erp-web\src\views\system\data-manage\index.vue'
$cd = Join-Path $erp 'beichen-erp-web\src\views\system\clear-data\index.vue'

Sec '0) F8-19 data: cleanup section 14 applied (menu 908 label, backup first)'
$o = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $erp 'tools\regression\audit\audit-20260929-fin-cleanup-fixtures.ps1') -Apply -Sections 14 2>&1
$o | Select-String -Pattern 'backup written|label clarified' | ForEach-Object { Write-Host ('    ' + $_.Line.Trim()) }

Sec '1) F8-16: clear-list covers every BASE TABLE that has company_id (minus exemptions)'
# list side: parse only the quoted "DELETE FROM <t> ..." entries of COMPANY_DELETES
$listTables = @(Select-String -Path $cc -Pattern '^\s*"DELETE FROM ([a-z_0-9]+)' |
    ForEach-Object { $_.Matches[0].Groups[1].Value.ToLower() } | Sort-Object -Unique)
Write-Host ('    tables in COMPANY_DELETES = ' + $listTables.Count)
# NOTE: do NOT use GROUP_CONCAT for the table list — it silently truncates at group_concat_max_len
# (1024 by default) and produced a bogus fragment ("o") in the first run. Use a NOT IN diff instead.
$dbTables = @(SqlAll "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema='beichen_erp' AND t.table_type='BASE TABLE' AND t.table_name NOT LIKE 'sys\_%' AND EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='beichen_erp' AND c.table_name=t.table_name AND c.column_name='company_id')")
$dbTables = @($dbTables | ForEach-Object { "$_".Trim().ToLower() } | Where-Object { $_ -ne '' } | Sort-Object -Unique)
$inList = ($listTables | ForEach-Object { "'" + $_ + "'" }) -join ', '
$missing = @(SqlAll "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema='beichen_erp' AND t.table_type='BASE TABLE' AND t.table_name NOT LIKE 'sys\_%' AND t.table_name <> 'screen_model' AND t.table_name NOT IN ($inList) AND EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='beichen_erp' AND c.table_name=t.table_name AND c.column_name='company_id')")
Write-Host ('    company-scoped BASE TABLEs = ' + $dbTables.Count + ' | missing from list = ' + $missing.Count)
if ($missing.Count -gt 0) { Write-Host ('    MISSING: ' + ($missing -join ', ')) }
Ok ($missing.Count -eq 0) 'F8-16 assertion: {company_id BASE TABLE} - {screen_model} is fully inside COMPANY_DELETES'

Sec '2) F8-16: the 12 previously-missing tables are now in the list AND actually hold rows'
foreach ($t in @('finance_receipt_account', 'finance_payment_account', 'purchase_exchange', 'purchase_exchange_item',
    'inventory_material_move', 'inventory_material_move_item', 'outsource_return_back', 'outsource_return_back_item',
    'outsource_material_return_repair', 'outsource_material_return_repair_material',
    'outsource_return_order_repair', 'outsource_return_order_repair_item')) {
  $inList = ($listTables -contains $t)
  $rows = SqlOne ("SELECT COUNT(*) FROM " + $t)
  Ok $inList ('in list: ' + $t.PadRight(42) + ' db rows=' + $rows)
}
Ok ([bool](Select-String -Path $cc -Pattern 'F8-16 新增' )) 'F8-16 markers present in ClearController'

Sec '2b) D-26 (user decision): sys_param + sys_operation_log are inside the wipe scope'
foreach ($t in @('sys_param', 'sys_operation_log')) {
  $rows = SqlOne ("SELECT COUNT(*) FROM " + $t)
  Ok ($listTables -contains $t) ('in list: ' + $t.PadRight(22) + ' db rows=' + $rows)
}
Ok ([bool](Select-String -Path $sc -Pattern 'sys_param", "sys_operation_log')) 'ClearTableSelfCheck asserts these two sys tables must stay in the list'
$sysKeep = SqlOne "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name IN ('sys_user','sys_role','sys_menu','sys_company')"
Ok ($sysKeep -eq '4') 'company/user/role/menu tables still exist and are NOT in the wipe list'
foreach ($t in @('sys_user', 'sys_role', 'sys_menu', 'sys_company')) {
  Ok (-not ($listTables -contains $t)) ('kept out of scope: ' + $t)
}

Sec '3) F8-17: clearing now requires the backend confirm word (early return, zero side effects)'
$before = @{}
foreach ($t in @('purchase_exchange', 'finance_receipt_account', 'outsource_return_back', 'warehouse_stock')) {
  $before[$t] = SqlOne ("SELECT COUNT(*) FROM " + $t)
}
$tok = Lg 'lin'
Ok ([bool]$tok) 'admin token acquired (lin)'
$r = ApiPost $tok '/system/clear-company-data' '{}'
Write-Host ('    POST without confirm -> code=' + $r.code + ' msg=' + $r.msg)
Ok ([int]$r.code -ne 200) 'clear-company-data refused without confirm'
Ok (("$($r.msg)") -match '未确认|请输入') 'refusal message tells the operator what to do'
$drift = 0
foreach ($t in $before.Keys) { if ((SqlOne ("SELECT COUNT(*) FROM " + $t)) -ne $before[$t]) { $drift++ } }
Ok ($drift -eq 0) 'nothing was deleted by the refused call (row counts unchanged)'

Sec '4) F8-17: dryRun reports what WOULD be deleted and writes a rollback point (no deletes)'
$dry = ApiPost $tok '/system/clear-company-data?dryRun=true' '{}'
Ok ([int]$dry.code -eq 200) ('dryRun returns code=' + $dry.code)
Ok ([int]$dry.data.tables -ge 90) ('dryRun table count = ' + $dry.data.tables)
$rowsMap = $dry.data.rows
$pe = if ($rowsMap.purchase_exchange) { [int]$rowsMap.purchase_exchange } else { -1 }
Write-Host ('    dryRun rows: purchase_exchange=' + $pe + ', finance_receipt_account=' + $rowsMap.finance_receipt_account + ', outsource_return_back=' + $rowsMap.outsource_return_back)
Ok ($pe -gt 0) 'previously-missed table (purchase_exchange) is now in the wipe scope'
$olRows = if ($rowsMap.sys_operation_log) { [int]$rowsMap.sys_operation_log } else { 0 }
Write-Host ('    dryRun rows: sys_param=' + $rowsMap.sys_param + ', sys_operation_log=' + $olRows)
Ok ([int]$dry.data.tables -ge 100) ('D-26: wipe scope = ' + $dry.data.tables + ' tables (98 business + sys_param + sys_operation_log)')
Ok ($olRows -ge 1) 'this company''s operation-log rows are inside the wipe scope (D-26)'
$bak = "$($dry.data.backup)"
Ok (Test-Path -LiteralPath $bak) ('rollback point written on disk: ' + $bak)
$sqlPe = [int](SqlOne 'SELECT COUNT(*) FROM purchase_exchange')
Ok ($pe -eq $sqlPe) 'dryRun per-table count matches direct SQL (E2 cross-check)'
$drift2 = 0
foreach ($t in $before.Keys) { if ((SqlOne ("SELECT COUNT(*) FROM " + $t)) -ne $before[$t]) { $drift2++ } }
Ok ($drift2 -eq 0) 'dryRun deleted nothing (row counts unchanged)'

Sec '5) F8-05: sys_operation_log now HAS a writer (the dryRun above left an audit row)'
$audit = SqlOne "SELECT COUNT(*) FROM sys_operation_log WHERE operation='清空公司数据' AND detail LIKE '%演练%'"
Write-Host ('    audit rows for the dryRun = ' + $audit)
Ok ([int]$audit -gt 0) 'dryRun wrote a sys_operation_log row (table was read-only before this fix)'
Ok ([bool](Select-String -Path $cc -Pattern 'operationLogMapper.insert')) 'ClearController writes operation logs'
Ok ([bool](Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\SystemController.java') -Pattern 'operationLogMapper.insert')) 'SystemController (import) writes operation logs'

Sec '6) authorization: low-privilege user cannot clear; full wipe needs super admin'
$low = Lg 'perm_test'
Ok ([bool]$low) 'low-privilege token acquired (perm_test / 销售专员)'
$rLow = ApiPost $low '/system/clear-company-data' '{}'
Write-Host ('    low-priv POST clear-company-data -> code=' + $rLow.code)
Ok ([int]$rLow.code -eq 403) 'clear-company-data is 403 for a non-admin'
$rWipe = ApiPost $tok '/system/clear-data' '{}'
Write-Host ('    admin POST clear-data (no confirm) -> code=' + $rWipe.code + ' msg=' + $rWipe.msg)
Ok ([int]$rWipe.code -ne 200) 'full wipe refused without confirm'
$drift3 = 0
foreach ($t in $before.Keys) { if ((SqlOne ("SELECT COUNT(*) FROM " + $t)) -ne $before[$t]) { $drift3++ } }
Ok ($drift3 -eq 0) 'still nothing deleted at the end of the run'

Sec '7) F8-18: preserve list is explicit + sanity guard exists'
Ok ([bool](Select-String -Path $cc -Pattern 'PRESERVE_TABLES')) 'PRESERVE_TABLES constant exists (was hard-coded in SQL)'
Ok ([bool](Select-String -Path $cc -Pattern '待清空表数异常')) 'sanity guard: abort when the enumerated table count is absurd'
$scr = SqlOne "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name='screen_model' AND table_type='BASE TABLE'"
Ok ($scr -eq '1') 'screen_model still exists (never in the wipe list)'

Sec '8) F8-19: menu label + front-end callers pass the confirm word'
$mn = SqlOne 'SELECT menu_name FROM sys_menu WHERE id=908'
Write-Host ('    sys_menu.id=908 name = ' + $mn)
Ok ("$mn" -eq '清空本公司数据') 'menu 908 renamed to 清空本公司数据'
Ok ([bool](Select-String -Path (Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\DataInitializer.java') -Pattern '清空本公司数据')) 'DataInitializer seeds the clarified label'
Ok ([bool](Select-String -Path $cd -Pattern 'params: \{ confirm: CONFIRM_WORD \}')) 'clear-data page sends confirm'
Ok ([bool](Select-String -Path $dm -Pattern 'params: \{ confirm: CLEAR_CONFIRM_WORD \}')) 'data-manage page sends confirm'
Ok ([bool](Select-String -Path $cd -Pattern 'dryRun: true')) 'clear-data page offers a dry run'
Ok ([bool](Select-String -Path $dm -Pattern 'dryRun: true')) 'data-manage page offers a dry run'

Sec '9) startup self-check is clean (ClearTableSelfCheck)'
$lg = Join-Path $erp 'beichen-erp-server\logs\beichen-erp.log'
$scLines = @(Select-String -Path $lg -Pattern 'clear-selfcheck')
$okIdx = -1
for ($i = 0; $i -lt $scLines.Count; $i++) { if ($scLines[$i].Line -match '清空清单一致性 OK') { $okIdx = $i } }
Ok ($okIdx -ge 0) 'ClearTableSelfCheck reported 清空清单一致性 OK at least once'
$after = if ($okIdx -ge 0) { $scLines[($okIdx + 1)..($scLines.Count - 1)] } else { @() }
Ok (-not [bool]($after | Where-Object { $_.Line -match '漏清单 ->' })) 'no missing-table entries after the latest clean block'

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
if ($fail -gt 0) { exit 1 }
