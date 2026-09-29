# D-7 (user approved option 1 on 2026-09-29): permission negative/positive tests with TEMPORARY restricted users.
# F7-225 (P0, fixed 2026-09-29): /api/supplier-settlement used to sit on the EXEMPT list although it has
# write endpoints. Step 4 below is the before/after pair:
#   - a user holding ONLY finance:payable  => POST return-materials / finish must be 403
#   - a user holding outsource:supplier    => the same POSTs must NOT be 403 (they reach the business layer)
#     (id 999999 does not exist, so a successful call cannot mutate anything.)
# The script CREATES two roles + two users and DELETES them in the finally block, then asserts that the four
# permission-table counts are back to the baseline. ASCII ONLY.
$ErrorActionPreference = 'Continue'
$base = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$env:MYSQL_PWD = 'root'
$script:PASS = 0; $script:FAIL = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Step($n) { Write-Host ('--- STEP ' + $n) }
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -N -B -e $q 2>$null
  $v = (@($o) | Where-Object { $_ -notmatch '^(mysql:|ERROR)' } | Select-Object -First 1)
  if ($null -eq $v) { return '' }
  return ("$v").Trim()
}
function Run([string]$sql) { & $MYSQL --default-character-set=utf8mb4 -uroot -D beichen_erp -e $sql 2>$null | Out-Null }
function Cn([int[]]$cp) { return (-join ($cp | ForEach-Object { [string][char]$_ })) }
$CN_NOPERM = Cn @(0x65E0,0x6743,0x9650)      # 无权限
$CN_AUDIT_ONLY = Cn @(0x5BA1,0x8BA1,0x2D,0x4EC5,0x5E94,0x4ED8)   # 审计-仅应付
function CodeOf($r) {
  if ($r -is [string]) { if ($r -match '403' -or $r -match [regex]::Escape($CN_NOPERM)) { return 403 }; return 0 }
  if ($null -eq $r) { return -1 }
  if ($r.code) { return [int]$r.code }
  return 200
}
function ApiAs($tok, $method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers @{ Authorization = $tok } -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers @{ Authorization = $tok }
  } catch { return $_.ErrorDetails.Message }
}
# creates role + user (menu looked up by perms) and logs in; returns @{ roleId, userId, token }
function MakeFixture([string]$roleCode, [string]$userName, [string]$perm, [string]$roleLabel) {
  $menuId = SqlOne ("SELECT id FROM sys_menu WHERE perms='" + $perm + "' LIMIT 1")
  if ($menuId -eq '') { throw ('no menu with perms=' + $perm) }
  $hash = SqlOne "SELECT password FROM sys_user WHERE username='lin'"
  Run ("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='" + $roleCode + "')")
  Run ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $userName + "')")
  Run ("DELETE FROM sys_role WHERE role_code='" + $roleCode + "'")
  Run ("DELETE FROM sys_user WHERE username='" + $userName + "'")
  Run ("INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('" + $roleLabel + "','" + $roleCode + "',1,'2026-09-29 audit fixture, safe to delete',1)")
  $rid = SqlOne ("SELECT id FROM sys_role WHERE role_code='" + $roleCode + "'")
  Run ("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (" + $rid + "," + $menuId + ")")
  Run ("INSERT INTO sys_user (username, password, status, company_id, deleted, menu_mode) VALUES ('" + $userName + "','" + $hash + "',1,1,0,'ROLE')")
  $uid = SqlOne ("SELECT id FROM sys_user WHERE username='" + $userName + "'")
  Run ("INSERT INTO sys_user_role (user_id, role_id) VALUES (" + $uid + "," + $rid + ")")
  $lg = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body ('{"username":"' + $userName + '","password":"123","companyId":1}')
  return @{ roleId = $rid; userId = $uid; token = $lg.data.token }
}
function DropFixture($f) {
  if ($f -eq $null) { return }
  if ($f.userId -ne '') { Run ("DELETE FROM sys_user_role WHERE user_id=" + $f.userId); Run ("DELETE FROM sys_user WHERE id=" + $f.userId) }
  if ($f.roleId -ne '') { Run ("DELETE FROM sys_role_menu WHERE role_id=" + $f.roleId); Run ("DELETE FROM sys_role WHERE id=" + $f.roleId) }
}

$r0 = SqlOne 'SELECT COUNT(*) FROM sys_role'; $u0 = SqlOne 'SELECT COUNT(*) FROM sys_user'
$ur0 = SqlOne 'SELECT COUNT(*) FROM sys_user_role'; $rm0 = SqlOne 'SELECT COUNT(*) FROM sys_role_menu'
Write-Host ('[BASE] roles=' + $r0 + ' users=' + $u0 + ' user_role=' + $ur0 + ' role_menu=' + $rm0)
$fin = $null; $sup = $null
try {
  Step '1) create two temporary users: (a) finance:payable only, (b) outsource:supplier only'
  $fin = MakeFixture 'AUDIT_PAYABLE_ONLY' 'audit_perm_payable' 'finance:payable' $CN_AUDIT_ONLY
  $sup = MakeFixture 'AUDIT_SUPPLIER_WRITE' 'audit_perm_supplier' 'outsource:supplier' $CN_AUDIT_ONLY
  Write-Host ('  finance-only: role=' + $fin.roleId + ' user=' + $fin.userId + ' ; supplier-write: role=' + $sup.roleId + ' user=' + $sup.userId)
  Ok ([bool]$fin.token -and [bool]$sup.token) 'both restricted users logged in'

  Step '2) finance:payable must pass on its own module and 403 elsewhere (mirror read isolation)'
  $cases = @(
    @{ p = '/finance/payable/page?pageSize=1'; want = 200; n = 'payable/page (own module)' },
    @{ p = '/finance/payable/payments?supplierId=26&pageSize=1'; want = 200; n = 'payable/payments (supplier workbench)' },
    @{ p = '/finance/payable/unpaid?supplierId=26'; want = 200; n = 'payable/unpaid (write-off picker)' },
    @{ p = '/finance/payment/page?pageSize=1'; want = 403; n = 'payment/page (mirror endpoint)' },
    @{ p = '/finance/payment/payable-summary'; want = 403; n = 'payment/payable-summary (mirror read)' },
    @{ p = '/finance/receipt/page?pageSize=1'; want = 403; n = 'receipt/page (unrelated)' },
    @{ p = '/finance/payable-transfer/transferable'; want = 403; n = 'payable-transfer/transferable (unrelated)' },
    @{ p = '/finance/bill/page?pageSize=1'; want = 403; n = 'bill/page (unrelated)' }
  )
  foreach ($c in $cases) {
    $code = CodeOf (ApiAs $fin.token 'Get' $c.p $null)
    Write-Host ('  ' + $c.p + ' -> ' + $code)
    Ok (($code -eq $c.want)) ($c.n + ' expected ' + $c.want + ' got ' + $code)
  }

  Step '3) F7-225 BEFORE/AFTER: writer endpoints of /api/supplier-settlement'
  $p1 = '/supplier-settlement/999999/return-materials'
  $p2 = '/supplier-settlement/999999/finish'
  $c1 = CodeOf (ApiAs $fin.token 'Post' $p1 '{"toWarehouseId":1}')
  Write-Host ('  finance-only  POST return-materials -> ' + $c1)
  Ok (($c1 -eq 403)) ('return-materials refused for a user without supplier perms (got ' + $c1 + ')')
  $c2 = CodeOf (ApiAs $fin.token 'Post' $p2 $null)
  Write-Host ('  finance-only  POST finish           -> ' + $c2)
  Ok (($c2 -eq 403)) ('finish refused for a user without supplier perms (got ' + $c2 + ')')
  $g = CodeOf (ApiAs $sup.token 'Get' '/supplier-settlement/999999' $null)
  Write-Host ('  supplier-perm GET  summary         -> ' + $g)
  $c3 = CodeOf (ApiAs $sup.token 'Post' $p1 '{"toWarehouseId":1}')
  Write-Host ('  supplier-perm POST return-materials -> ' + $c3)
  Ok (($c3 -ne 403)) ('return-materials still allowed WITH outsource:supplier (got ' + $c3 + ' = reached business layer)')
  $c4 = CodeOf (ApiAs $sup.token 'Post' $p2 $null)
  Write-Host ('  supplier-perm POST finish           -> ' + $c4)
  Ok (($c4 -ne 403)) ('finish still allowed WITH outsource:supplier (got ' + $c4 + ')')
  $c5 = CodeOf (ApiAs $sup.token 'Get' '/finance/payable/page?pageSize=1' $null)
  Write-Host ('  supplier-perm GET /finance/payable/page -> ' + $c5)
  Ok (($c5 -eq 403)) ('the supplier-side user still cannot read the finance module (got ' + $c5 + ')')
} finally {
  DropFixture $sup
  DropFixture $fin
  $r1 = SqlOne 'SELECT COUNT(*) FROM sys_role'; $u1 = SqlOne 'SELECT COUNT(*) FROM sys_user'
  $ur1 = SqlOne 'SELECT COUNT(*) FROM sys_user_role'; $rm1 = SqlOne 'SELECT COUNT(*) FROM sys_role_menu'
  Write-Host ('[AFTER] roles=' + $r1 + ' users=' + $u1 + ' user_role=' + $ur1 + ' role_menu=' + $rm1)
  Ok (($r1 -eq $r0) -and ($u1 -eq $u0) -and ($ur1 -eq $ur0) -and ($rm1 -eq $rm0)) 'temporary roles/users fully removed (counts back to baseline)'
}
Write-Host ('RESULT perm D-7 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
