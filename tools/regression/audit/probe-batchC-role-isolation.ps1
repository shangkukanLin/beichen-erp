# Batch C (settings audit, 2026-09-30) - role isolation verification (S-9②).
# READ MOSTLY: only "no-op" writes - PUT /system/role/{id}/menus with the role's CURRENT menu ids
# (idempotent rewrite, nothing changes) - plus session company switches (self-cleaning: switches back).
# Verifies: per-company role lists, cross-company denial, platform-role editability (F8-11 fix), and
# that the super admin can still see everything from the platform context (companyId=0).
# ASCII-only.
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$base = 'http://localhost:8080/api'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function SqlOne([string]$sql) {
  $l = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
    Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
  return ("$l").Trim()
}
function Lg($u, $p, $cid) {
  try { return (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
      -Body ('{"username":"' + $u + '","password":"' + $p + '","companyId":' + $cid + '}')).data.token } catch { return $null }
}
function Api($tok, $method, $path, $json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok } -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok }
  } catch { return @{ code = [int]$_.Exception.Response.StatusCode.value__; msg = $_.Exception.Message } }
}

# role ids from the migration
$company1Finance = SqlOne "SELECT id FROM sys_role WHERE role_code='finance' AND company_id=1"
$company2Finance = SqlOne "SELECT id FROM sys_role WHERE role_code='finance' AND company_id=2"
$platformFinance = SqlOne "SELECT id FROM sys_role WHERE role_code='finance' AND company_id=0"
$platformRoleIds = SqlOne "SELECT GROUP_CONCAT(id ORDER BY id) FROM sys_role WHERE company_id=0"
Write-Host '=== batch C role-isolation probe (S-9/2) ==='
Write-Host ('    company1.finance=' + $company1Finance + '  company2.finance=' + $company2Finance + '  platform.finance=' + $platformFinance)

$lin = Lg 'lin' '123' 1
Ok ([bool]$lin) 'lin login (company context 1) ok'

Sec '1) company context: role list is scoped to the current company'
$r = Api $lin 'Get' '/system/role/page?pageNum=1&pageSize=50' $null
$cids = @($r.data.records | ForEach-Object { $_.companyId } | Sort-Object -Unique)
Write-Host ('    company-1 list: total=' + $r.data.total + ' distinct companyIds=[' + ($cids -join ',') + ']')
Ok (($cids.Count -eq 1) -and ($cids[0] -eq 1)) 'company-1 context only sees company 1 roles'

Sec '2) cross-company edition is denied, own-company edition works (no-op menu rewrite)'
$ownMenus = SqlOne ("SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_role_menu WHERE role_id=" + $company1Finance)
$r = Api $lin 'Put' ('/system/role/' + $company2Finance + '/menus') ('[' + (($ownMenus -split ',')[0..4] -join ',') + ']')
Write-Host ('    edit company-2 role from company-1 -> code=' + $r.code + ' msg=' + $r.msg)
Ok ($r.code -eq 403) 'cross-company role menu edit is denied (403)'
$ownAll = '[' + $ownMenus + ']'
$before = SqlOne ("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=" + $company1Finance)
$r = Api $lin 'Put' ('/system/role/' + $company1Finance + '/menus') $ownAll
$after = SqlOne ("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=" + $company1Finance)
Write-Host ('    rewrite own role menus (same ids) -> code=' + $r.code + ' rows ' + $before + ' -> ' + $after)
Ok (($r.code -eq 200) -and ($after -eq $before)) 'own-company role menus are editable (idempotent rewrite)'

Sec '3) F8-11 fix: platform roles are editable from the PLATFORM context (companyId=0)'
$plat = $null
try {
  $plat = (Invoke-RestMethod -Uri ($base + '/company/admin/verify') -Method Post -ContentType 'application/json' `
      -Body '{"username":"lin","password":"123"}').data.token
} catch { }
Ok ([bool]$plat) 'platform (companyId=0) session obtained via /company/admin/verify'
if ($plat) {
  $r = Api $plat 'Get' '/system/role/page?pageNum=1&pageSize=50' $null
  Write-Host ('    platform list: total=' + $r.data.total)
  Ok ([int]$r.data.total -ge 19) 'platform context sees ALL roles (incl. every company copy)'
  $pMenus = SqlOne ("SELECT GROUP_CONCAT(menu_id ORDER BY menu_id) FROM sys_role_menu WHERE role_id=" + $platformFinance)
  $before2 = SqlOne ("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=" + $platformFinance)
  $r = Api $plat 'Put' ('/system/role/' + $platformFinance + '/menus') ('[' + $pMenus + ']')
  $after2 = SqlOne ("SELECT COUNT(*) FROM sys_role_menu WHERE role_id=" + $platformFinance)
  Write-Host ('    platform role menu rewrite -> code=' + $r.code + ' rows ' + $before2 + ' -> ' + $after2)
  Ok ($r.code -eq 200) 'F8-11 fixed: a platform role can now be maintained (was 403 for everyone)'
}

Sec '4) data面: every company has its own role set; user mapping stays consistent'
foreach ($cid in 1, 2) {
  $n = SqlOne ("SELECT COUNT(*) FROM sys_role WHERE company_id=" + $cid)
  $u = SqlOne ("SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.company_id=" + $cid + " AND r.company_id=" + $cid)
  Write-Host ('    company ' + $cid + ': roles=' + $n + ' matched user-role links=' + $u)
  Ok ([int]$n -eq 6) ('company ' + $cid + ' has its own 6 role copies')
}
$mis = SqlOne "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE IFNULL(u.company_id,0)<>0 AND IFNULL(r.company_id,0)<>0 AND r.company_id<>u.company_id"
Ok ($mis -eq '0') 'no cross-company user-role residue'
$sup = SqlOne "SELECT COUNT(*) FROM sys_role WHERE role_code='super_admin'"
Ok ($sup -eq '1') 'super_admin stays a single platform role'

Write-Host ''
Write-Host ('RESULT  PASS=' + $pass + '  FAIL=' + $fail)
if ($fail -gt 0) { exit 1 } else { exit 0 }
