# =====================================================================================
# F8-22 fix verification (2026-09-30, settings batch E follow-up): declarative role->menu plan.
# Read-only for the live DB **except** one transaction that is rolled back (the negative/positive
# control below), so the net change is always zero.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\verify-fix-f8-22.ps1
# =====================================================================================
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$repo = 'C:\Users\75629\CodeBuddy\20260710123705'
$erp = Join-Path $repo 'beichen-erp'
$src = Join-Path $erp 'beichen-erp-server\src\main\java\com\beichen\erp'
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
$rs = Join-Path $src 'system\service\impl\RoleServiceImpl.java'
$roleEntity = Join-Path $src 'system\entity\Role.java'

Write-Host '=== F8-22 fix verification (declarative role->menu plan) ==='

Sec '1) plan table + marker column exist'
Ok ((SqlOne "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='beichen_erp' AND table_name='sys_role_menu_plan'") -eq '1') 'sys_role_menu_plan table exists'
Ok ((SqlOne "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='beichen_erp' AND table_name='sys_role' AND column_name='customized_menu'") -eq '1') 'sys_role.customized_menu column exists'
Ok (Has (Join-Path $erp 'beichen-erp-server\src\main\resources\db\migration\V1__base.sql') 'sys_role_menu_plan') 'V1__base.sql declares the plan table (fresh installs)'
$planRows = SqlOne 'SELECT COUNT(*) FROM sys_role_menu_plan'
Write-Host ('    plan rows = ' + $planRows)
Ok ([int]$planRows -gt 0) 'plan was snapshotted on startup (non-empty)'

Sec '2) wiring: startup replay + user-edit marker'
Ok (Has $di 'private void initRoleMenuPlan') 'DataInitializer.initRoleMenuPlan() exists'
Ok (Has $di 'initRoleMenuPlan\(\);') 'it is called from the startup sequence'
Ok (Has $di 'IFNULL\(r\.customized_menu, 0\) = 0') 'replay skips roles the user has customised'
Ok (Has $rs 'setCustomizedMenu\(1\)') 'RoleServiceImpl.saveRoleMenus stamps customized_menu=1'
Ok (Has $roleEntity 'customizedMenu') 'Role entity exposes customizedMenu'

Sec '3) invariant: every non-customised role has all its plan menus'
$missing = SqlOne @'
SELECT COUNT(*) FROM sys_role_menu_plan p JOIN sys_role r ON r.role_code = p.role_code
WHERE IFNULL(r.customized_menu,0) = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = p.menu_id)
'@
Write-Host ('    (role, menu) pairs missing = ' + $missing)
Ok ("$missing" -eq '0') 'no non-customised role is missing a planned menu (replay converged)'

Sec '4) negative/positive control (RUN INSIDE A TRANSACTION THAT IS ROLLED BACK -> net change 0)'
$probe = SqlOne @'
SELECT CONCAT(r.id, ',', r.role_code) FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id = r.id
WHERE IFNULL(r.customized_menu,0) = 0 ORDER BY r.id LIMIT 1
'@
Ok ([bool]$probe) ('picked a control role: ' + $probe)
if ($probe) {
    $parts = $probe -split ','
    $rid = [int]$parts[0]
    $mid = SqlOne ("SELECT menu_id FROM sys_role_menu WHERE role_id = " + $rid + " ORDER BY menu_id LIMIT 1")
    Write-Host ('    control role id=' + $rid + ' role_code=' + $parts[1] + ' menu_id=' + $mid)
    $script = @"
START TRANSACTION;
UPDATE sys_role SET customized_menu = 1 WHERE id = $rid;
DELETE FROM sys_role_menu WHERE role_id = $rid AND menu_id = $mid;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, p.menu_id FROM sys_role_menu_plan p JOIN sys_role r ON r.role_code = p.role_code
WHERE IFNULL(r.customized_menu, 0) = 0 AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = p.menu_id);
SELECT CONCAT('customised_after=', (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = $rid AND menu_id = $mid));
UPDATE sys_role SET customized_menu = 0 WHERE id = $rid;
DELETE FROM sys_role_menu WHERE role_id = $rid AND menu_id = $mid;
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, p.menu_id FROM sys_role_menu_plan p JOIN sys_role r ON r.role_code = p.role_code
WHERE IFNULL(r.customized_menu, 0) = 0 AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = p.menu_id);
SELECT CONCAT('normal_after=', (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = $rid AND menu_id = $mid));
ROLLBACK;
SELECT CONCAT('after_rollback=', (SELECT COUNT(*) FROM sys_role_menu WHERE role_id = $rid AND menu_id = $mid), '/customized=', (SELECT IFNULL(customized_menu,0) FROM sys_role WHERE id = $rid));
"@
    $out = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $script 2>&1) |
        Where-Object { "$_" -notmatch '^(mysql:|Using a password)' }
    $out | ForEach-Object { Write-Host ('    ' + $_) }
    $joined = ($out -join ' ')
    Ok ($joined -match 'customised_after=0') 'NEGATIVE: a user-customised role does NOT get its removed menu back'
    Ok ($joined -match 'normal_after=1') 'POSITIVE: a normal role does get the plan menu re-granted'
    Ok ($joined -match 'after_rollback=1/customized=0') 'the control transaction was rolled back (net change 0)'
}

Sec '5) startup log'
$lg = Join-Path $erp 'beichen-erp-server\logs\beichen-erp.log'
Ok (Has $lg '\[F8-22\]') 'startup logged the F8-22 snapshot/replay'

Write-Host ''
Write-Host ('RESULT: ' + $pass + ' passed, ' + $fail + ' failed')
if ($fail -gt 0) { exit 1 }
