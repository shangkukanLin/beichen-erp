# =====================================================================================
# Settings batch E fix verification (2026-09-30) — F8-20 / F8-21 / F8-23 / F8-24 / F8-25 / F8-26 / F8-10 / F8-09
# Read-only (the only writes happen in the cleanup script sections 15/16 which are applied here WITH a backup).
# ASCII-only on purpose (no BOM required under PS 5.1). Prints PASS/FAIL + a final RESULT line.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-fix-f8-20-26.ps1
# =====================================================================================
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$erp = Join-Path $repo 'beichen-erp'
$src = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp'
$web = Join-Path $erp 'beichen-erp-web\src'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function SqlOne([string]$sql) {
    $l = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
        Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
    return ("$l").Trim()
}
function Has($path, $pattern) { return [bool](Select-String -Path $path -Pattern $pattern -Quiet) }

$di = Join-Path $src 'config\DataInitializer.java'
$us = Join-Path $src 'system\service\impl\UserServiceImpl.java'
$ms = Join-Path $src 'system\service\impl\MenuServiceImpl.java'
$menuEntity = Join-Path $src 'system\entity\Menu.java'

Write-Host '=== settings batch E fix verification (F8-20..F8-26, F8-10, F8-09) ==='

Sec '0) F8-23 / F8-09 data corrections: cleanup sections 15 + 16 (backup first)'
$o = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $erp 'tools\regression\audit\audit-20260929-fin-cleanup-fixtures.ps1') -Apply -Sections 15,16 2>&1
$o | Select-String -Pattern 'backup written|orphan material_type|deleted \(F8-23\)|tagged \(F8-09\)' | ForEach-Object { Write-Host ('    ' + $_.Line.Trim()) }

Sec '1) F8-21: customized marker wiring (schema + entity + write path + sync logic)'
Ok ((SqlOne "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='beichen_erp' AND table_name='sys_menu' AND column_name='customized'") -eq '1') 'sys_menu.customized column exists in the live DB'
Ok (Has (Join-Path $erp 'beichen-erp-server\src\main\resources\db\migration\V1__base.sql') 'customized') 'V1__base.sql defines the customized column (fresh installs get it)'
Ok (Has $di 'IF\(customized=1, menu_name') 'syncMenus honours customized=1 for menu_name (and friends)'
Ok (-not (Has $di 'addColumnIfMissing')) 'P1: DataInitializer performs no startup DDL (sys_menu.customized is declared in schema.sql)'
Ok (Has $menuEntity 'private Integer customized') 'Menu entity exposes customized'
Ok (Has $ms 'entity.setCustomized\(1\)') 'MenuServiceImpl.updateById stamps customized=1 on user edits'
Ok (Has $ms 'entity.getCustomized\(\) == null') 'MenuServiceImpl.save stamps customized=1 for user-created menus'

Sec '2) F8-20: startup migrations now run before the port opens'
Ok (Has $di '@jakarta\.annotation\.PostConstruct') 'DataInitializer runs from @PostConstruct'
Ok (-not (Has $di 'implements ApplicationRunner')) 'no longer an ApplicationRunner (port was already open)'

Sec '3) F8-24 / F8-25: honest class docs + fail-fast preflight'
Ok (-not (Has $di 'private\s+\w+\s+migrate[A-Z]')) 'P1: no migrate*() method remains (schema.sql is the single source of truth)'
Ok (-not (Has $di 'migrate[A-Z]\w*\(\);')) 'P1: no migrate*() call line remains in init()'
Ok (Has $di 'private void assertSchemaReady') 'assertSchemaReady() preflight exists'
Ok (Has $di 'assertSchemaReady\(\);') 'preflight is actually called at startup'
Ok (Has $di 'P1') 'the class javadoc carries the P1 pre-launch cleanup note (migrations removed)'

Sec '4) F8-23: every company has default business seeds'
$cids = @(SqlOne "SELECT GROUP_CONCAT(id ORDER BY id) FROM sys_company") -split ','
Write-Host ('    companies = ' + ($cids -join ','))
foreach ($c in $cids) {
    $pt = SqlOne ("SELECT COUNT(*) FROM dev_phase_template WHERE company_id = " + $c)
    $ct = SqlOne ("SELECT COUNT(*) FROM outsource_contract_template WHERE company_id = " + $c)
    $mt = SqlOne ("SELECT COUNT(*) FROM material_type WHERE company_id = " + $c)
    Ok ([int]$pt -gt 0) ('company ' + $c + ' has phase templates: ' + $pt)
    Ok ([int]$ct -gt 0) ('company ' + $c + ' has contract templates: ' + $ct)
    Ok ([int]$mt -gt 0) ('company ' + $c + ' has material types: ' + $mt)
}
$orph = SqlOne 'SELECT COUNT(*) FROM material_type m WHERE NOT EXISTS (SELECT 1 FROM sys_company c WHERE c.id = m.company_id)'
Write-Host ('    orphan material_type rows = ' + $orph)
Ok ("$orph" -eq '0') 'no orphan tenant rows left in material_type (cleanup section 15)'
Ok (Has $di 'backfillCompanyDefaults') 'startup backfill method exists (idempotent, copy-from-company-1)'

Sec '5) F8-10: users can no longer be created with a NULL tenant'
Ok (Has $us '请先选择要创建用户的公司') 'UserServiceImpl refuses to create a user without a company'
$nullUsers = SqlOne 'SELECT COUNT(*) FROM sys_user WHERE company_id IS NULL'
Write-Host ('    sys_user rows with company_id IS NULL = ' + $nullUsers)
Ok ("$nullUsers" -eq '0') 'no NULL-tenant users in the live DB'

Sec '6) F8-26: v-perm coverage on the system pages + quoting convention'
$sysHits = @(Get-ChildItem (Join-Path $web 'views\system') -Recurse -Filter *.vue | Select-String -Pattern 'v-perm').Count
Write-Host ('    v-perm hits under views/system = ' + $sysHits)
Ok ($sysHits -ge 17) 'the 7 system pages now carry button-level permissions'
$badQuote = @(Get-ChildItem $web -Recurse -Filter *.vue | Select-String -Pattern "v-perm='").Count
Ok ($badQuote -eq 0) ('no v-perm with the wrong quoting (should be v-perm=""''code''""): ' + $badQuote)

Sec '7) F8-09: orphan attribution tagged (admin3)'
$orphUsers = SqlOne 'SELECT COUNT(*) FROM sys_user u WHERE NOT EXISTS (SELECT 1 FROM sys_company c WHERE c.id = u.company_id) AND IFNULL(u.deleted,0) = 0'
$orphRows = SqlOne 'SELECT COUNT(*) FROM sys_user u WHERE NOT EXISTS (SELECT 1 FROM sys_company c WHERE c.id = u.company_id)'
Write-Host ('    orphan-attributed users (all / not-soft-deleted) = ' + $orphRows + ' / ' + $orphUsers)
# 口径：只留痕不改归属（该行 deleted=1）；这里断言"活跃用户里没有孤儿归属" + 清理脚本 §16 用只读清单暴露它们
Ok ("$orphUsers" -eq '0') 'no ACTIVE user points at a non-existent company'
Ok (Has (Join-Path $erp 'tools\regression\audit\audit-20260929-fin-cleanup-fixtures.ps1') 'orphan user attribution listed above') 'cleanup section 16 lists orphan user attribution (read-only, sys_user has no remark column)'

Sec '8) startup log is clean'
$lg = Join-Path $erp 'beichen-erp-server\logs\beichen-erp.log'
Ok (Has $lg '\[F8-23\] 已为其它公司补齐默认数据') 'startup backfill logged the rows it added (F8-23 evidence)'
Ok (-not (Has $lg '\[启动初始化失败\]')) 'no startup preflight failure in the log'

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
if ($fail -gt 0) { exit 1 }
