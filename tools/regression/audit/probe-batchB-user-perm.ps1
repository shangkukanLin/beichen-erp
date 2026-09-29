# Batch B (settings audit, 2026-09-30) - READ + SELF-CLEANING WRITE probe.
# Purpose: prove/refute two privilege findings on the user-management surface:
#   F8-06: can a same-company ADMIN reset the SUPER-ADMIN's password (escalation)?
#   F8-07: can a same-company ADMIN disable the SUPER-ADMIN via PUT /system/user (vs toggleStatus which guards it)?
# Self-cleaning discipline: snapshot lin's row -> run -> RESTORE via SQL -> verify counts/hash restored.
# Touched rows (all restored): sys_user id=1 (password, status) + the temporary probe user.
# ASCII-only by design (Chinese needles built from code points).
$ErrorActionPreference = 'Continue'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$base = 'http://localhost:8080/api'
$pass = 0; $fail = 0
function Ok($c, $m) { if ($c) { $script:pass++; Write-Host ('  PASS  ' + $m) } else { $script:fail++; Write-Host ('  FAIL  ' + $m) } }
function Sec($t) { Write-Host ''; Write-Host ('### ' + $t) }
function Cn([int[]]$cp) { return (-join ($cp | ForEach-Object { [char]$_ })) }
function SqlOne([string]$sql) {
  $l = @(& $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $sql 2>$null) |
    Where-Object { "$_" -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1
  return ("$l").Trim()
}
function Run([string]$sql) { & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e $sql 2>$null | Out-Null }
function Lg($u, $p, $cid) {
  try { return (Invoke-RestMethod -Uri ($base + '/auth/login') -Method Post -ContentType 'application/json' `
      -Body ('{"username":"' + $u + '","password":"' + $p + '","companyId":' + $cid + '}')).data.token } catch { return $null }
}
function Api($tok, $method, $path, $json) {
  try {
    if ($json) {
      $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok } -ContentType 'application/json' -Body $json
    } else {
      $r = Invoke-RestMethod -Uri ($base + $path) -Method $method -Headers @{ Authorization = $tok }
    }
    return ('code=' + $r.code + ' msg=' + $r.msg)
  } catch { return ('HTTP ' + $_.Exception.Response.StatusCode.value__ + ' ' + $_.Exception.Message) }
}
$CN_SUPER = Cn @(0x8D85,0x7EA7,0x7BA1,0x7406,0x5458)                 # 超级管理员
$TEMP = 'zz_probe_admin_tmp'
$PROBE_PW = 'probe_pw_9x7'

Write-Host '=== batch B user-permission probe (self-cleaning) ==='

Sec '0) snapshot before any change'
$hash0 = SqlOne "SELECT password FROM sys_user WHERE id=1"
$status0 = SqlOne "SELECT status FROM sys_user WHERE id=1"
$users0 = SqlOne "SELECT COUNT(*) FROM sys_user"
$roles0 = SqlOne "SELECT COUNT(*) FROM sys_user_role"
$roleRows0 = SqlOne "SELECT GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_user_role WHERE user_id=1"
Write-Host ('    lin(password)=' + $hash0.Substring(0, [Math]::Min(20, $hash0.Length)) + '...  status=' + $status0 + '  users=' + $users0 + '  user_roles=' + $roles0)
$bak = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'audit-batchB-user-backup.txt'
@("id=1 password=$hash0 status=$status0", "users=$users0 user_roles=$roles0") | Set-Content -LiteralPath $bak -Encoding UTF8

try {
  Sec '1) login: lin (super admin) and create a temporary COMPANY-1 ADMIN user'
  $lin = Lg 'lin' '123' 1
  Ok ([bool]$lin) 'lin login ok'
  $body = '{"username":"' + $TEMP + '","password":"' + $PROBE_PW + '","phone":null,"dept":"probe","status":1,"roleIds":[1]}'
  Write-Host ('    create -> ' + (Api $lin 'Post' '/system/user' $body))
  $tmpId = SqlOne ("SELECT id FROM sys_user WHERE username='" + $TEMP + "'")
  Write-Host ('    temp user id = ' + $tmpId)
  Ok ($tmpId -ne '' -and $tmpId -ne '0') 'temporary same-company admin created'
  $tmp = Lg $TEMP $PROBE_PW 1
  Ok ([bool]$tmp) 'temporary admin can log in'

  Sec '2) control/negative: cross-company reset must be refused (admin2 is company 2)'
  $r = Api $tmp 'Put' '/system/user/reset-password' '{"id":3,"password":"whatever123"}'
  Write-Host ('    cross-company reset -> ' + $r)
  Ok ($r -match '403') 'cross-company password reset is refused (403)'

  Sec '3) F8-06 (AFTER FIX): same-company reset of the SUPER ADMIN must be refused'
  $r = Api $tmp 'Put' '/system/user/reset-password' ('{"id":1,"password":"' + $PROBE_PW + '"}')
  Write-Host ('    reset lin password -> ' + $r)
  Ok ($r -match '403') 'F8-06 fixed: an ADMIN can no longer reset the SUPER ADMIN password'
  $lin2 = Lg 'lin' $PROBE_PW 1
  Ok (-not [bool]$lin2) 'the probe password does NOT grant a super-admin session'
  Ok ([bool](Lg 'lin' '123' 1)) 'lin still logs in with the original password (nothing changed)'

  Sec '4) F8-07 / F8-08 (AFTER FIX): disable + role wipe through PUT /system/user'
  $r = Api $tmp 'Put' '/system/user/1/status' $null
  Write-Host ('    toggleStatus -> ' + $r)
  Ok (($r -match [regex]::Escape($CN_SUPER))) 'toggleStatus still refuses to disable the super admin'
  # NOTE: UserDTO requires a non-blank username (@Valid) - payload carries lin's own username + current role ids.
  $linRoles = SqlOne "SELECT GROUP_CONCAT(role_id) FROM sys_user_role WHERE user_id=1"
  Write-Host ('    lin role ids = ' + $linRoles)
  $r = Api $tmp 'Put' '/system/user' ('{"id":1,"username":"lin","phone":null,"dept":null,"status":0,"roleIds":[' + $linRoles + '],"dashboardTabs":[]}')
  Write-Host ('    updateUser(status=0, same payload that reproduced F8-07) -> ' + $r)
  $st = SqlOne "SELECT status FROM sys_user WHERE id=1"
  $rr = SqlOne "SELECT GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_user_role WHERE user_id=1"
  Write-Host ('    lin.status now = ' + $st + '   lin role ids now = ' + $rr)
  Ok ($r -match '403') 'F8-07 fixed: updateUser refuses to change the super admin'
  Ok ($st -eq '1') 'super admin status untouched'
  Ok ($rr -eq $roleRows0) 'F8-08 fixed: role rows untouched by an edit attempt'
  # positive control: the super admin editing HIMSELF still works (and keeps the super_admin role)
  $r = Api $lin 'Put' '/system/user' ('{"id":1,"username":"lin","phone":null,"dept":null,"status":1,"roleIds":[' + $linRoles + '],"dashboardTabs":[]}')
  Write-Host ('    super admin edits himself -> ' + $r)
  Ok ($r -match 'code=200') 'positive control: the super admin may still edit his own profile'
  $rr2 = SqlOne "SELECT GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_user_role WHERE user_id=1"
  Ok ($rr2 -eq $roleRows0) 'F8-08 fixed: self-edit keeps the super_admin role row'
}
finally {
  Sec '5) RESTORE (mandatory): lin row + delete the temporary user'
  Run ("UPDATE sys_user SET password='" + $hash0 + "', status=" + $status0 + " WHERE id=1")
  # IMPORTANT: PUT /system/user also rebuilds the role rows and saveUserRoles() **skips super_admin**
  # (new finding F8-08) => lin's super_admin row disappears. Re-insert every snapshot role id.
  foreach ($rid in ($roleRows0 -split ',')) { if ("$rid".Trim() -ne '') { Run ("INSERT INTO sys_user_role (user_id, role_id) SELECT 1, " + "$rid".Trim() + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id=1 AND role_id=" + "$rid".Trim() + ")") } }
  Run ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $TEMP + "')")
  Run ("DELETE FROM sys_user_dashboard_tab WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $TEMP + "')")
  Run ("DELETE FROM sys_user WHERE username='" + $TEMP + "'")
  $hash1 = SqlOne "SELECT password FROM sys_user WHERE id=1"
  $status1 = SqlOne "SELECT status FROM sys_user WHERE id=1"
  $users1 = SqlOne "SELECT COUNT(*) FROM sys_user"
  $roles1 = SqlOne "SELECT COUNT(*) FROM sys_user_role"
  Write-Host ('    lin(password restored)=' + ($hash1 -eq $hash0) + '  status=' + $status1 + '  users=' + $users1 + '  user_roles=' + $roles1)
  Ok ($hash1 -eq $hash0) 'lin password hash restored byte-for-byte'
  Ok ($status1 -eq $status0) 'lin status restored'
  Ok ($users1 -eq $users0) 'sys_user row count restored'
  Ok ($roles1 -eq $roles0) 'sys_user_role row count restored'
  Ok ((Lg 'lin' '123' 1) -ne $null) 'lin can still log in with the original password 123'
}

Write-Host ''
Write-Host ('RESULT  PASS=' + $pass + '  FAIL=' + $fail)
Write-Host ('backup: ' + $bak)
