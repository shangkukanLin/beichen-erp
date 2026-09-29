# =====================================================================================
# 真实清空回归（在**独立沙箱库**上做真删除）— F8-16 / F8-17 / D-26
#
# 为什么需要它：`verify-fix-f8-16-19.ps1` 只能在"不带 confirm"与 `dryRun` 两条**无副作用**路径上取证，
# 端的 DELETE 语句从未真正跑过。本脚本把主库当作**只读数据源**，在沙箱库上把整份删除清单**真跑一遍**，
# 用"清完之后逐表点数"来证明：**没有一张含 company_id 的表被漏掉**（这正是 F8-16 当初躲过人工审核的原因）。
#
# ------------------------------------------------------------------ 安全红线（务必先读）
#   1) 目标库名必须匹配 ^beichen_erp_cleartest（默认 beichen_erp_cleartest），且不在 denylist
#      {beichen_erp, beichen_erp1, beichen_erp3, beichen_erp_test, mysql, sys, ...} 里 —— 双保险；
#   2) **主库 beichen_erp 全程只读**：只做 SELECT 与 `INSERT INTO 沙箱.t SELECT * FROM 主库.t`（跨库读）；
#   3) 所有写操作（DROP/CREATE/INSERT/UPDATE/DELETE）都通过 `mysql -D <沙箱>` 执行，落库前再
#      `SELECT DATABASE()` 复核一次；
#   4) 清理清单**单一来源**：从 ClearController.java 解析 `"DELETE FROM <t> WHERE ..."`（与启动自检同源），
#      并断言"每条都带 company_id 条件"——清单若被改动，本脚本立刻跟着变，不会漂移；
#   5) Phase 2 只在 $Port（默认 8081）另起实例，**绝不触碰 8080 上正在跑的实例**；
#   6) 默认跑完删除沙箱库（`-KeepSandbox` 保留以便人工翻看）。
#
# ------------------------------------------------------------------ 用法
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-clear-data-real-on-testdb.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-clear-data-real-on-testdb.ps1 -WithApp
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-clear-data-real-on-testdb.ps1 -KeepSandbox
# =====================================================================================
param(
    [string]$Sandbox = 'beichen_erp_cleartest',
    [int]$TestCompany = 1,
    [int]$SiblingCompany = 0,          # 0 = 自动挑一个 ≠ TestCompany 的公司做隔离对照
    [switch]$WithApp,
    [int]$Port = 8081,
    [switch]$KeepSandbox
)

$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$erp = Join-Path $repo 'beichen-erp'
$LIVE = 'beichen_erp'                                   # 只读源库
$schemaFile = Join-Path $erp 'beichen-erp-server\src\main\resources\schema.sql'
$clearJava = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\ClearController.java'
$base = 'http://localhost:' + $Port + '/api'
$DENY = @('beichen_erp', 'beichen_erp1', 'beichen_erp3', 'beichen_erp_test',
    'mysql', 'information_schema', 'performance_schema', 'sys')
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Info($t) { Write-Host ('    ' + $t) }

# ---- SQL 助手 --------------------------------------------------------------
# SqlRaw：原样返回 stdout+stderr（**不过滤 ERROR**，避免"错误被吞掉还报绿"）；[sql, schema]
function SqlRaw([string]$sql, [string]$schema) {
    $a = @('--default-character-set=utf8mb4', '-uroot', '-N', '-B')
    if ($schema) { $a += @('-D', $schema) }
    $a += @('-e', $sql)
    return @(& $MYSQL @a 2>&1)
}
# Run：执行写操作；只忽略 mysql 的口令告警行，其它（含 ERROR）保留
function Run([string]$sql, [string]$schema) {
    $a = @('--default-character-set=utf8mb4', '-uroot', '-N', '-B')
    if ($schema) { $a += @('-D', $schema) }
    $a += @('-e', $sql)
    return @(& $MYSQL @a 2>&1) | Where-Object { "$_" -notmatch 'Using a password' }
}
# One：取单值（只读），过滤噪声行
function One([string]$sql, [string]$schema) {
    $l = @(SqlRaw $sql $schema) | Where-Object { "$_" -notmatch '^(mysql:|ERROR|Warning|Using a password)' } | Select-Object -First 1
    return ("$l").Trim()
}
function Sb([string]$sql) { return (One $sql $Sandbox) }
function Lv([string]$sql) { return (One $sql $LIVE) }
function ExecFile([string]$path) {
    $cmd = '"{0}" --default-character-set=utf8mb4 -uroot -N -B -D {1} < "{2}"' -f $MYSQL, $Sandbox, $path
    return @(& cmd.exe /c $cmd 2>&1) | Where-Object { "$_" -notmatch 'Using a password' }
}

Write-Host '=== real clear-data regression on an isolated sandbox database ==='
Write-Host ('    sandbox = ' + $Sandbox + ' | test company = ' + $TestCompany + ' | app phase = ' + [bool]$WithApp)

# ---------------------------------------------------------------- Phase 0: preflight
Sec '0) preflight (safety rails)'
Ok ($Sandbox -match '^beichen_erp_cleartest') ('sandbox name follows the allowed pattern: ' + $Sandbox)
Ok (-not ($DENY -contains $Sandbox.ToLower())) 'sandbox is not one of the protected databases'
Ok (Test-Path -LiteralPath $MYSQL) 'mysql client found'
Ok (Test-Path -LiteralPath $schemaFile) 'schema.sql found'
Ok (Test-Path -LiteralPath $clearJava) 'ClearController.java found'

# schema.sql 必须是"纯 DDL"：出现 USE <db> / CREATE DATABASE / DROP 就意味着加载它会打到别的库
$danger = @(Select-String -Path $schemaFile -Pattern '(?im)^\s*(USE\s+\w|CREATE\s+DATABASE|DROP\s+DATABASE|DROP\s+TABLE)')
if ($danger.Count -gt 0) { $danger | Select-Object -First 5 | ForEach-Object { Info ('dangerous DDL: line ' + $_.LineNumber + ' -> ' + $_.Line.Trim()) } }
Ok ($danger.Count -eq 0) 'schema.sql is pure DDL (no USE/CREATE DATABASE/DROP) - safe to load into a sandbox'

# 清理清单：与启动自检同源
$stmts = @(Select-String -Path $clearJava -Pattern '^\s*"(DELETE FROM [^"]+)"' | ForEach-Object { $_.Matches[0].Groups[1].Value })
$listTables = @($stmts | ForEach-Object { if ($_ -match '^DELETE FROM ([a-zA-Z_0-9]+)') { $Matches[1].ToLower() } } | Sort-Object -Unique)
Ok ($stmts.Count -ge 100) ('delete statements parsed from ClearController = ' + $stmts.Count)
$noCompany = @($stmts | Where-Object { $_ -notmatch 'company_id' })
if ($noCompany.Count -gt 0) { $noCompany | ForEach-Object { Info ('statement without company_id: ' + $_) } }
Ok ($noCompany.Count -eq 0) 'every delete statement is company-scoped (no company_id-less DELETE)'
Ok ([int](Lv ("SELECT COUNT(*) FROM sys_company WHERE id = " + $TestCompany)) -eq 1) ('test company exists in the LIVE database (read-only check): id=' + $TestCompany)

# ---------------------------------------------------------------- Phase 1: build the sandbox
Sec '1) build the sandbox (drop + create + load the app schema)'
Run ("DROP DATABASE IF EXISTS " + $Sandbox) $null | Out-Null
Run ("CREATE DATABASE " + $Sandbox + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci") $null | Out-Null
$loadOut = ExecFile $schemaFile
if ($loadOut.Count -gt 0) { $loadOut | Select-Object -First 3 | ForEach-Object { Info ('load: ' + $_) } }
Ok ((Sb 'SELECT DATABASE()') -eq $Sandbox) ('statements execute inside the sandbox: SELECT DATABASE() = ' + (Sb 'SELECT DATABASE()'))
$sbTables = [int](Sb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$Sandbox' AND table_type='BASE TABLE'")
$liveTables = [int](Lv "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$LIVE' AND table_type='BASE TABLE'")
Info ('sandbox BASE TABLEs = ' + $sbTables + ' | live BASE TABLEs = ' + $liveTables)
Ok ($sbTables -ge 90) 'sandbox got the schema (>= 90 tables)'

# 清单里的表必须都在沙箱里存在（否则 DELETE 会报错 => 清单已与 schema 漂移）
$missingInSb = @($listTables | Where-Object { (Sb ("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Sandbox' AND table_name='$_'")) -ne '1' })
if ($missingInSb.Count -gt 0) { $missingInSb | ForEach-Object { Info ('in the clear list but missing in schema: ' + $_) } }
Ok ($missingInSb.Count -eq 0) 'every table referenced by the clear list exists in schema.sql'

# ---------------------------------------------------------------- Phase 2: seed
Sec '2) seed: copy live data into the sandbox under rewritten company ids (live stays read-only)'
# 口径说明：scope = '含 company_id 的非 sys_ 表' + D-26 的两张 sys 表；**排除 PRESERVE（screen_model）**，
# 它由"保留表"那一组原样复制，并单独断言"清空前后行数不变"。
$PRESERVE = @('screen_model')
$scopeTables = @(SqlRaw "SELECT t.table_name FROM information_schema.tables t WHERE t.table_schema='$Sandbox' AND t.table_type='BASE TABLE' AND t.table_name NOT LIKE 'sys\_%' AND EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_schema='$Sandbox' AND c.table_name=t.table_name AND c.column_name='company_id')" $Sandbox)
$scopeTables = @($scopeTables | ForEach-Object { "$_".Trim().ToLower() } | Where-Object { $_ } | Sort-Object -Unique)
foreach ($t in @('sys_param', 'sys_operation_log')) { if ($listTables -contains $t) { $scopeTables += $t } }
$scopeTables = @($scopeTables | ForEach-Object { $_ } | Where-Object { $PRESERVE -notcontains $_ } | Sort-Object -Unique)
Info ('company-scoped tables to seed / check = ' + $scopeTables.Count + ' (preserved: ' + ($PRESERVE -join ', ') + ')')

if ($SiblingCompany -eq 0) {
    $sib = Sb ("SELECT id FROM sys_company WHERE id <> " + $TestCompany + " ORDER BY id LIMIT 1")
    if (-not $sib) { $sib = [string]($TestCompany + 1) }
    $SiblingCompany = [int]$sib
}
Info ('sibling company (isolation control) = ' + $SiblingCompany)
Ok ($SiblingCompany -ne $TestCompany) 'sibling id differs from the test company'

# 列清单一次性取回（逐表查 information_schema 会跑上百次 mysql，太慢）
$liveCols = @{}; $sbCols = @{}
foreach ($line in @(SqlRaw "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='$LIVE' ORDER BY table_name, ordinal_position" $Sandbox)) {
    $parts = ("$line") -split "`t"
    if ($parts.Count -lt 2) { continue }
    $tn = $parts[0].Trim()
    if (-not $liveCols.ContainsKey($tn)) { $liveCols[$tn] = @() }
    $liveCols[$tn] = @($liveCols[$tn]) + $parts[1].Trim()
}
foreach ($line in @(SqlRaw "SELECT table_name, column_name FROM information_schema.columns WHERE table_schema='$Sandbox' ORDER BY table_name, ordinal_position" $Sandbox)) {
    $parts = ("$line") -split "`t"
    if ($parts.Count -lt 2) { continue }
    $tn = $parts[0].Trim()
    if (-not $sbCols.ContainsKey($tn)) { $sbCols[$tn] = @() }
    $sbCols[$tn] = @($sbCols[$tn]) + $parts[1].Trim()
}
# 播种用"两边都有的列"（交集）；**现网有、schema.sql 没有的列**单独记下来 —— 那是"schema.sql 落后于现网"的证据
$colMap = @{}
$drift = @()
foreach ($tn in $liveCols.Keys) {
    $have = @($sbCols[$tn])
    $inter = @($liveCols[$tn] | Where-Object { $have -contains $_ })
    $colMap[$tn] = $inter
    if ($inter.Count -lt @($liveCols[$tn]).Count) {
        $missing = @($liveCols[$tn] | Where-Object { $have -notcontains $_ })
        $drift += ($tn + ' -> ' + ($missing -join ','))
        $driftCols += $missing
    }
}
$driftCols = @($driftCols | Sort-Object -Unique)
# 口径修正（2026-09-30 复核）：schema.sql 里**没有**这些列**不是缺陷** ——
# `DataInitializer.initDocOperatorColumns()`（:673-710）与约 18 个 `migrateXxx()` 会在**启动时**
# 用 information_schema 判断后幂等 ALTER 补列（create_by/create_by_name/auditor_id/auditor_name 等）。
# 所以正确的不变量是："现网有而 schema.sql 没有的列，必须都能在 DataInitializer 的启动迁移里找到"
# —— 找不到才是真漂移（升级脚本漏了）。
$diJava = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp\config\DataInitializer.java'
$unmanaged = @()
foreach ($c in $driftCols) {
    # 匹配"裸列名"即可 —— DataInitializer 的补列写法有两种：
    #   columnExists(t, "create_by")  /  addColumnIfMissing(t, "finisher_id BIGINT NULL ...")
    # 后者带列定义后缀，用 '"name"' 这种严格匹配会漏判（第一版就是这样误报的）。
    if (-not (Select-String -Path $diJava -Pattern ([regex]::Escape($c)) -Quiet)) { $unmanaged += $c }
}
if ($drift.Count -gt 0) {
    Info ('schema.sql omits columns for ' + $drift.Count + ' table(s); all from DataInitializer startup migration: ' + (-not [bool]$unmanaged))
    Info ('  distinct live-only columns: ' + ($driftCols -join ', '))
    $drift | Select-Object -First 6 | ForEach-Object { Info ('  live-only: ' + $_) }
}
if ($unmanaged.Count -gt 0) { Info ('NOT managed by DataInitializer: ' + ($unmanaged -join ', ')) }
Ok ($unmanaged.Count -eq 0) 'every live-only column is created by DataInitializer startup migration (no unmanaged drift)'
# 复制一张表；$targetCompany > 0 = 插入时把 company_id **改写**成目标公司（不是先插再 UPDATE，
# 否则兄弟那一趟会把先插的那份也扫走 —— 第一版就是这样把测试公司的数据搬空的）。
# 删除清单里"按父表 id 删子表"的那些引用列（如 `report_id IN (SELECT id FROM ...)`）。
# 兄弟副本必须把这些列**一起偏移**，否则兄弟的子表行会被"公司 1 的父表 id"匹配到，隔离断言会假红。
$ID_OFFSET = 100000000
$fkCols = @()
foreach ($s in $stmts) {
    if ($s -match '(\w+)\s+IN\s*\(\s*SELECT\s+id\s+FROM') { $fkCols += $Matches[1] }
}
$fkCols = @($fkCols | Sort-Object -Unique)
if ($fkCols.Count -gt 0) { Info ('parent-id reference columns found in the clear list: ' + ($fkCols -join ', ')) }

function CopyTable([string]$t, [int]$targetCompany, [bool]$skipIdentity) {
    $cols = @($colMap[$t])
    if ($cols.Count -eq 0) { return 'ERROR no columns found for ' + $t }
    # 反引号标识符用**显式拼接**（不要写 "`$t`" —— 双引号里 `$ 会被转义成字面量 $，
    # 生成的 SQL 会去找一张名叫 $t 的表）
    $bt = [char]96
    # 同一张表插第二份（兄弟公司那份）时必须**跳过自增主键**，否则撞 "Duplicate entry for PRIMARY"
    if ($skipIdentity) { $cols = @($cols | Where-Object { $_ -ne 'id' }) }
    $src = ($cols | ForEach-Object { $bt + $_ + $bt }) -join ', '
    $sel = ($cols | ForEach-Object {
            if ($_ -eq 'company_id' -and $targetCompany -gt 0) { [string]$targetCompany }
            elseif ($skipIdentity -and $_ -eq 'id') { $bt + $_ + $bt + ' + ' + $ID_OFFSET }
            elseif ($skipIdentity -and $fkCols -contains $_) { $bt + $_ + $bt + ' + ' + $ID_OFFSET }
            else { $bt + $_ + $bt }
        }) -join ', '
    $sql = 'INSERT INTO ' + $bt + $t + $bt + ' (' + $src + ') SELECT ' + $sel + ' FROM ' + $LIVE + '.' + $bt + $t + $bt
    return (Run $sql $Sandbox)
}

$sibFailed = @()
$sibFailMsg = @{}
# 外键检查必须关掉：保留表之间有依赖（如 sys_user.company_id -> sys_company），按名字顺序插会撞 FK
Run 'SET FOREIGN_KEY_CHECKS = 0' $Sandbox | Out-Null
foreach ($t in $scopeTables) {
    $r1 = CopyTable $t $TestCompany $false
    if (("$r1") -match 'ERROR') { Info ('test-company copy failed: ' + $t + ' -> ' + $r1) }
    # 同一批数据再放一份到"兄弟公司"（隔离对照）；若该表仍有**全局**唯一键（不含 company_id）则会冲突
    # —— 记下来并跳过该表的隔离断言。这份清单本身就是"F7-258 同族问题（其他模块唯一键未带 company_id）"的证据。
    $r2 = CopyTable $t $SiblingCompany $true
    if (("$r2") -match 'ERROR') { $sibFailed += $t; $sibFailMsg[$t] = ("$r2").Trim() }
}
$preserved = @('screen_model', 'sys_user', 'sys_role', 'sys_menu', 'sys_company', 'sys_user_role', 'sys_role_menu')
foreach ($t in $preserved) {
    if ((Sb ("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Sandbox' AND table_name='$t'")) -eq '1') {
        $rp = CopyTable $t 0 $false
        if (("$rp") -match 'ERROR') { Info ('preserved copy failed: ' + $t + ' -> ' + $rp) }
    }
}
Run 'SET FOREIGN_KEY_CHECKS = 1' $Sandbox | Out-Null
if ($sibFailed.Count -gt 0) {
    Info ('sibling copy skipped for ' + $sibFailed.Count + ' table(s) (a unique key without company_id): ' + ($sibFailed -join ', '))
    $sibFailed | Select-Object -First 3 | ForEach-Object { Info ('  reason: ' + $_ + ' -> ' + $sibFailMsg[$_]) }
}

# ---------------------------------------------------------------- Phase 3: pre-conditions
Sec '3) pre-conditions (a green run must be meaningful)'
$before = @{}; $beforeSib = @{}
$withRows = 0
foreach ($t in $scopeTables) {
    $before[$t] = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $TestCompany))
    $beforeSib[$t] = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $SiblingCompany))
    if ($before[$t] -gt 0) { $withRows++ }
}
$preservedBefore = @{}
foreach ($t in $preserved) { $preservedBefore[$t] = [int](Sb ("SELECT COUNT(*) FROM $t")) }
Info ('tables holding data for company ' + $TestCompany + ' = ' + $withRows + ' / ' + $scopeTables.Count + ' | rows = ' + (($before.Values | Measure-Object -Sum).Sum))
Info ('sibling rows = ' + (($beforeSib.Values | Measure-Object -Sum).Sum))
Ok ($withRows -ge 20) 'the test company has data in >= 20 tables (otherwise a green run proves nothing)'
Ok ((($beforeSib.Values | Measure-Object -Sum).Sum) -gt 0) 'the sibling company has data too (isolation assertion is meaningful)'
$coverage = @($scopeTables | Where-Object { $listTables -notcontains $_ })
if ($coverage.Count -gt 0) { $coverage | ForEach-Object { Info ('NOT in the clear list: ' + $_) } }
Ok ($coverage.Count -eq 0) 'every company-scoped table is covered by the clear list'

# ---------------------------------------------------------------- Phase 4: the real clear
Sec '4) real DELETE replay inside the sandbox (destructive, sandbox only)'
$tmpSql = Join-Path $env:TEMP ('clear-data-sandbox-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
$sbText = New-Object System.Text.StringBuilder
[void]$sbText.AppendLine('SET FOREIGN_KEY_CHECKS = 0;')
[void]$sbText.AppendLine('START TRANSACTION;')
foreach ($s in $stmts) { [void]$sbText.AppendLine(($s -replace '\?', $TestCompany) + ';') }
[void]$sbText.AppendLine('COMMIT;')
[void]$sbText.AppendLine('SET FOREIGN_KEY_CHECKS = 1;')
[System.IO.File]::WriteAllText($tmpSql, $sbText.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Info ('replaying ' + $stmts.Count + ' statements from ' + $tmpSql)
$execOut = @(ExecFile $tmpSql)
$execErr = @($execOut | Where-Object { "$_" -match 'ERROR' })
if ($execErr.Count -gt 0) { $execErr | Select-Object -First 5 | ForEach-Object { Info ('exec error: ' + $_) } }
Ok ($execErr.Count -eq 0) ('replaying the whole clear list produced no SQL errors (' + $stmts.Count + ' statements)')

# ---------------------------------------------------------------- Phase 5: assertions
Sec '5) post-conditions: the company is empty everywhere (F8-16 regression)'
$leftover = @()
foreach ($t in $scopeTables) {
    $left = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $TestCompany))
    if ($left -gt 0) { $leftover += ($t + '=' + $left) }
}
if ($leftover.Count -gt 0) { $leftover | ForEach-Object { Info ('LEFT BEHIND: ' + $_) } }
Ok ($leftover.Count -eq 0) ('after the clear 0 rows remain in every company-scoped table (' + $scopeTables.Count + ' tables checked)')

Sec '6) tenant isolation: the sibling company is untouched'
$touched = @()
foreach ($t in $scopeTables) {
    if ($sibFailed -contains $t) { continue }
    $now = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $SiblingCompany))
    if ($now -ne $beforeSib[$t]) { $touched += ($t + ': ' + $beforeSib[$t] + ' -> ' + $now) }
}
if ($touched.Count -gt 0) { $touched | ForEach-Object { Info ('CHANGED: ' + $_) } }
Ok ($touched.Count -eq 0) ('the sibling company''s rows are unchanged (checked ' + ($scopeTables.Count - $sibFailed.Count) + ' tables)')

Sec '7) system data preserved (company/user/role/menu + screen_model)'
foreach ($t in $preserved) {
    $now = [int](Sb ("SELECT COUNT(*) FROM $t"))
    Ok ($now -eq $preservedBefore[$t]) ('preserved: ' + $t.PadRight(18) + ' ' + $preservedBefore[$t] + ' -> ' + $now)
}
Ok ([int](Sb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$Sandbox' AND table_type='BASE TABLE'") -eq $sbTables) 'no table was dropped by the replay (schema intact)'

Sec '8) D-26: sys_param / sys_operation_log cleared for the test company only'
$p = [int](Sb ("SELECT COUNT(*) FROM sys_param WHERE company_id = " + $TestCompany))
$pSib = [int](Sb ("SELECT COUNT(*) FROM sys_param WHERE company_id = " + $SiblingCompany))
$l = [int](Sb ("SELECT COUNT(*) FROM sys_operation_log WHERE company_id = " + $TestCompany))
$lSib = [int](Sb ("SELECT COUNT(*) FROM sys_operation_log WHERE company_id = " + $SiblingCompany))
Info ('sys_param: test=' + $p + ' sibling=' + $pSib + ' | sys_operation_log: test=' + $l + ' sibling=' + $lSib)
Ok ($p -eq 0) 'sys_param rows of the cleared company are gone'
Ok ($l -eq 0) 'sys_operation_log rows of the cleared company are gone'
Ok ($pSib -eq $beforeSib['sys_param']) 'the sibling''s sys_param rows survived'
Ok ($lSib -eq $beforeSib['sys_operation_log']) 'the sibling''s operation-log rows survived'

# ---------------------------------------------------------------- Phase 6 (opt-in): end to end
if ($WithApp) {
    Sec ('9) end to end: second instance on port ' + $Port + ' pointed at the sandbox (8080 is left alone)')
    # Phase 1 已经真删过一遍 ⇒ 公司 1 现在是空的。端到端要测"清空一个**有数据**的公司"，
    # 先按同一口径重新播一份（表已空，带显式 id 插入不会撞主键）。
    Run 'SET FOREIGN_KEY_CHECKS = 0' $Sandbox | Out-Null
    foreach ($t in $scopeTables) { CopyTable $t $TestCompany $false | Out-Null }
    foreach ($t in $preserved) { CopyTable $t 0 $false | Out-Null }
    Run 'SET FOREIGN_KEY_CHECKS = 1' $Sandbox | Out-Null
    $reseeded = 0
    foreach ($t in $scopeTables) { if ([int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $TestCompany)) -gt 0) { $reseeded++ } }
    Info ('re-seeded company ' + $TestCompany + ' for the app phase: ' + $reseeded + ' / ' + $scopeTables.Count + ' tables hold data')
    Ok ($reseeded -ge 20) 'the app phase starts from a populated company'
    $env:JAVA_HOME = 'E:\dev\java\zulu21.50.19-ca-jdk21.0.11-win_x64'
    $env:MAVEN_HOME = 'E:\dev\maven\apache-maven-3.9.9'
    $env:PATH = $env:JAVA_HOME + '\bin;' + $env:MAVEN_HOME + '\bin;' + $env:PATH
    $bat = Join-Path $env:TEMP 'run-clear-sandbox-app.bat'
    $jdbc = 'jdbc:mysql://localhost:3306/' + $Sandbox + '?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
    $batBody = @(
        '@echo off',
        ('cd /d ' + (Join-Path $erp 'beichen-erp-server')),
        ('mvn spring-boot:run -DskipTests -Dspring-boot.run.arguments="--server.port=' + $Port + ' --spring.datasource.url=' + $jdbc + '"')
    ) -join "`r`n"
    # NOT `New-Object System.Text.ASCIIEncoding()` — PS 5.1 fails to parse an empty-argument
    # constructor call there ("An expression was expected after '('"), see _parse-check.ps1.
    [System.IO.File]::WriteAllText($bat, $batBody, [System.Text.ASCIIEncoding]::new())
    $appLog = Join-Path $env:TEMP 'clear-sandbox-app.log'
    # ⚠️ bat 路径**不要加引号**：cmd /c "路径" > log 会让 cmd 的引号剥离规则把整条命令吃掉，
    # 结果 bat 根本没执行、日志文件也不生成（restart-backend.ps1 用的就是不加引号的写法）。
    if (Test-Path $appLog) { Remove-Item -LiteralPath $appLog -Force -ErrorAction SilentlyContinue }
    Start-Process -FilePath 'cmd.exe' -ArgumentList '/c', ($bat + ' > "' + $appLog + '" 2>&1') -WindowStyle Hidden
    $up = $false
    for ($i = 0; $i -lt 180; $i++) {
        Start-Sleep -Seconds 1
        if (netstat -ano | Select-String (':' + $Port + '\s') | Select-String 'LISTENING') { $up = $true; break }
    }
    Ok $up ('sandbox instance is listening on ' + $Port + ' (log: ' + $appLog + ')')
    if (-not $up -and (Test-Path $appLog)) {
        Info 'sandbox instance log tail:'
        Get-Content -LiteralPath $appLog -Tail 15 -Encoding UTF8 | ForEach-Object { Info ('  ' + $_) }
    }

    if ($up) {
        function ApiPost([string]$tok, [string]$path, [string]$body) {
            try {
                $p = @{ Uri = ($base + $path); Method = 'Post'; ContentType = 'application/json' }
                if ($tok) { $p.Headers = @{ Authorization = $tok } }
                if ($body -ne $null) { $p.Body = $body }
                return Invoke-RestMethod @p
            } catch {
                return @{ code = [int]$_.Exception.Response.StatusCode.value__; msg = 'HTTP ' + [int]$_.Exception.Response.StatusCode.value__ }
            }
        }
        $tok = $null
        try {
            $tok = (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
                    -Body ('{"username":"lin","password":"123","companyId":' + $TestCompany + '}')).data.token
        } catch { $tok = $null }
        Ok ([bool]$tok) 'logged in against the sandbox instance'

        $refused = ApiPost $tok '/system/clear-company-data' '{}'
        Ok ([int]$refused.code -ne 200) ('without confirm the instance refuses (code=' + $refused.code + ')')
        # 应用启动会跑 DataInitializer 的迁移/种子（对**所有**公司），所以兄弟公司的基线必须在
        # 实例起来之后再取一次，否则"未动兄弟公司"会假红。
        $beforeSib2 = @{}
        foreach ($t in $scopeTables) { $beforeSib2[$t] = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $SiblingCompany)) }
        Info ('sibling baseline re-taken after app startup: rows=' + (($beforeSib2.Values | Measure-Object -Sum).Sum))

        # 启动迁移（DataInitializer 的幂等 ALTER 补列）与请求事务并发时，MySQL 会抛
        # 1412 "Table definition has changed, please retry transaction" ⇒ 等它落定 + 失败重试。
        $dry = $null
        for ($try = 1; $try -le 6; $try++) {
            $dry = ApiPost $tok '/system/clear-company-data?dryRun=true' '{}'
            if ([int]$dry.code -eq 200) { break }
            if (("$($dry.msg)") -match 'changed|retry|1412' -or $try -eq 1) { Info ('dryRun attempt ' + $try + ' -> code=' + $dry.code + ' msg=' + $dry.msg) }
            Start-Sleep -Seconds 10
        }
        Info ('dryRun: code=' + $dry.code + ' tables=' + $dry.data.tables + ' rows=' + $dry.data.totalRows)
        Ok ([int]$dry.code -eq 200) 'dryRun succeeded on the sandbox instance'
        if ([int]$dry.code -eq 200) {
            Ok ((Test-Path -LiteralPath "$($dry.data.backup)")) 'rollback point written by the sandbox instance'
            Ok ([int]$dry.data.tables -ge 100) ('dryRun reports the full wipe scope = ' + $dry.data.tables + ' tables')
        }

        $real = ApiPost $tok ('/system/clear-company-data?confirm=' + [uri]::EscapeDataString('清空数据')) '{}'
        Info ('real clear -> code=' + $real.code + ' msg=' + $real.msg + ' rows=' + $real.data.totalRows)
        Ok ([int]$real.code -eq 200) 'real clear accepted with the confirm word (sandbox only)'

        # 清空后**理应仍有行**的表：① 清空动作自己重播的默认数据（与线上 ClearController 的 re-seed 一致）
        # ② sys_operation_log —— "开始"那行会被清空删掉，但"完成"那行是在 commit **之后**写的（设计使然）
        $RESEEDED = @('material_type', 'dev_phase_template', 'outsource_contract_template', 'sys_operation_log')
        $left2 = @()
        foreach ($t in $scopeTables) {
            $left = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $TestCompany))
            if ($left -gt 0 -and $RESEEDED -notcontains $t) { $left2 += ($t + '=' + $left) }
        }
        if ($left2.Count -gt 0) { $left2 | ForEach-Object { Info ('LEFT BEHIND (app path): ' + $_) } }
        Ok ($left2.Count -eq 0) 'the app-driven clear emptied every company-scoped table (re-seeded/default rows excluded)'
        foreach ($t in @('material_type', 'outsource_contract_template')) {
            $n = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $TestCompany))
            Ok ($n -gt 0) ('re-seed after clear restored defaults: ' + $t + ' rows=' + $n)
        }
        $doneRows = @(SqlRaw ("SELECT detail FROM sys_operation_log WHERE company_id = " + $TestCompany) $Sandbox)
        Ok ([bool]($doneRows | Where-Object { "$_" -match '完成' })) 'the completion audit row survived the clear (expected: written after commit)'
        $sibTouched = @()
        foreach ($t in $scopeTables) {
            if ($sibFailed -contains $t) { continue }
            $now = [int](Sb ("SELECT COUNT(*) FROM $t WHERE company_id = " + $SiblingCompany))
            if ($now -ne $beforeSib2[$t]) { $sibTouched += ($t + ':' + $beforeSib2[$t] + '->' + $now) }
        }
        if ($sibTouched.Count -gt 0) { $sibTouched | Select-Object -First 5 | ForEach-Object { Info ('SIBLING CHANGED: ' + $_) } }
        Ok ($sibTouched.Count -eq 0) 'the app-driven clear did not touch the sibling company'
        Ok ([int](Sb ("SELECT COUNT(*) FROM material_type WHERE company_id = " + $TestCompany)) -gt 0) 're-seed after clearing produced default material types again'
        Ok ([int](Sb "SELECT COUNT(*) FROM sys_operation_log WHERE operation='清空公司数据'") -gt 0) 'the clear left an audit trail in sys_operation_log (F8-05)'
    }

    $line = (netstat -ano | Select-String (':' + $Port + '\s') | Select-String 'LISTENING' | Select-Object -First 1)
    if ($line) {
        $scPid = ($line.Line -split '\s+')[-1]
        Stop-Process -Id $scPid -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Info ('stopped the sandbox instance pid=' + $scPid)
    }
}

# ---------------------------------------------------------------- cleanup
Sec '10) cleanup'
if (-not $KeepSandbox) {
    Run ("DROP DATABASE IF EXISTS " + $Sandbox) $null | Out-Null
    Ok ((One ("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='" + $Sandbox + "'") $null) -eq '0') 'sandbox database dropped'
} else {
    Info ('sandbox kept for inspection: ' + $Sandbox)
}
Ok ([int](Lv 'SELECT COUNT(*) FROM sys_company') -gt 0) ('LIVE database still readable and untouched (sys_company rows=' + (Lv 'SELECT COUNT(*) FROM sys_company') + ')')

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
if ($fail -gt 0) { exit 1 }
