# =====================================================================================
# Batch H cross-cutting probe (2026-09-30) - READ ONLY (SELECTs + file scans, no writes).
#   (a) frontend v-perm codes vs sys_menu.perms  -> dead / typo'd codes
#   (b) sequence generators (%03d) inventory     -> which ones re-check for collisions
#   (c) @RequestBody entity bindings             -> mass-assignment surface inventory
#   (d) tenant column gaps                       -> rows with NULL company_id, global unique keys
# ASCII-only by design (safe under Windows PowerShell 5.1 without a BOM).
# =====================================================================================
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705\beichen-erp'
$web = Join-Path $repo 'beichen-erp-web\src'
$srv = Join-Path $repo 'beichen-erp-server\src\main\java\com\beichen\erp'
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function SqlAll([string]$sql) {
  @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
    Where-Object { "$_" -notmatch '^(mysql:|ERROR)' }
}

Write-Host '=== batch H cross-cutting probe (read only) ==='

Sec 'A) frontend v-perm codes that do NOT exist in sys_menu.perms (dead buttons)'
$codes = @{}
foreach ($f in (Get-ChildItem -Path $web -Recurse -Include *.vue, *.ts)) {
  $txt = Get-Content -LiteralPath $f.FullName -Encoding UTF8 -Raw
  foreach ($m in [regex]::Matches($txt, "v-perm=""'([^']+)'""")) { $codes[$m.Groups[1].Value] = $f.Name }
}
Write-Host ('    distinct v-perm codes used in frontend: ' + $codes.Count)
$dbPerms = @{}
foreach ($r in SqlAll "SELECT DISTINCT perms FROM sys_menu WHERE perms IS NOT NULL AND perms <> ''") { $dbPerms["$r".Trim()] = $true }
Write-Host ('    distinct perms seeded in sys_menu     : ' + $dbPerms.Count)
$dead = @($codes.Keys | Where-Object { -not $dbPerms.ContainsKey($_) } | Sort-Object)
Write-Host ('    NOT IN sys_menu ("dead" codes)        : ' + $dead.Count)
foreach ($d in $dead) { Write-Host ('      dead -> ' + $d + '   (first seen in ' + $codes[$d] + ')') }
foreach ($k in ($codes.Keys | Sort-Object)) { Write-Host ('      used -> ' + $k) }

Sec 'B) sequence generators: file:line of every %03d formatting site (retry check is manual)'
foreach ($m in (Select-String -Path (Join-Path $srv '*\*.java'), (Join-Path $srv '*\*\*.java'), (Join-Path $srv '*\*\*\*.java') -Pattern '%03d' -ErrorAction SilentlyContinue)) {
  Write-Host ('    ' + ($m.Path -replace [regex]::Escape($srv + '\'), '') + ':' + $m.LineNumber + ': ' + $m.Line.Trim())
}

Sec 'C) @RequestBody bindings (mass-assignment surface): type -> count'
$bind = @{}
foreach ($m in (Select-String -Path (Join-Path $srv '*\*.java'), (Join-Path $srv '*\*\*.java'), (Join-Path $srv '*\*\*\*.java') -Pattern '@RequestBody\s+(?:@\w+\s+)?([A-Z]\w+)' -ErrorAction SilentlyContinue)) {
  $t = $m.Matches[0].Groups[1].Value
  if (-not $bind.ContainsKey($t)) { $bind[$t] = 0 }
  $bind[$t]++
}
foreach ($k in ($bind.Keys | Sort-Object -Descending { $bind[$_] })) { Write-Host ('    ' + $bind[$k].ToString().PadLeft(3) + '  ' + $k) }

Sec 'D) tenant gaps: NULL company_id rows per finance table + unique keys without company_id'
foreach ($t in @('finance_account', 'finance_receivable', 'finance_payable', 'finance_cashflow', 'finance_receipt', 'finance_payment', 'finance_expense', 'finance_invoice', 'finance_bill', 'finance_settlement')) {
  $nulls = @(SqlAll ("SELECT COUNT(*) FROM " + $t + " WHERE company_id IS NULL"))[0]
  $all = @(SqlAll ("SELECT COUNT(*) FROM " + $t))[0]
  Write-Host ('    ' + $t.PadRight(22) + ' rows=' + $all + '  null_company_id=' + $nulls)
}
$ukNoCid = SqlAll "SELECT COUNT(*) FROM (SELECT table_name FROM information_schema.statistics WHERE table_schema='beichen_erp' AND table_name LIKE 'finance\_%' AND non_unique=0 AND index_name<>'PRIMARY' GROUP BY table_name, index_name HAVING SUM(CASE WHEN column_name='company_id' THEN 1 ELSE 0 END)=0) x"
Write-Host ('    finance unique keys WITHOUT company_id: ' + $ukNoCid)

Sec 'E) duplicate (company, number) pairs across finance tables (should be 0)'
foreach ($p in @(@('finance_cashflow', 'flow_no'), @('finance_receipt', 'code'), @('finance_payment', 'code'), @('finance_expense', 'expense_no'), @('finance_receivable', 'bill_no'), @('finance_payable', 'bill_no'), @('finance_bill', 'bill_no'))) {
  $d = SqlAll ("SELECT COUNT(*) FROM (SELECT " + $p[1] + " FROM " + $p[0] + " GROUP BY company_id, " + $p[1] + " HAVING COUNT(*) > 1) x")
  Write-Host ('    ' + $p[0].PadRight(22) + ' duplicate numbers = ' + $d)
}

Sec 'F) rows whose company_id is not among sys_company / orphaned tenant (data hygiene)'
foreach ($table in @('finance_cashflow', 'finance_account', 'finance_expense')) {
  $orphan = SqlAll ("SELECT COUNT(*) FROM " + $table + " t WHERE t.company_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_company c WHERE c.id = t.company_id)")
  Write-Host ('    ' + $table.PadRight(22) + ' orphan_company_id = ' + $orphan)
}

Write-Host ''
Write-Host 'DONE (no writes performed).'
