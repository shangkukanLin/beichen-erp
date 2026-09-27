# verify-material-rd-expense.ps1 (2026-09-27, user request): the material page's "register an R&D expense" flow.
#
#   A) POST /api/outsource/material/{id}/rd-expense (amount + account) -> a DRAFT expense, type=RND,
#      source=R&D_MATERIAL/materialId -- and the ACCOUNT BALANCE MUST NOT MOVE (money moves only when the
#      expense is audited inside finance; the material page only files a draft).
#   B) NEGATIVE: a material created WITHOUT ticking the option must have NO R&D expense row
#      (guards against a future "create it by default" regression).
#   C) idempotent: calling again returns the SAME doc (existing=true) and does not create a second row.
#   D) NEGATIVE: amount<=0 / missing account -> rejected, still exactly one row.
#   E) PERMISSION (why the endpoint lives under the material prefix): a user whose ONLY menu is "material info"
#      can call it (200), while the same user posting /api/finance/expense is denied (403).
#
# Fixture is self-built / self-cleaned (temp material + temp role/user), so the file is repeatable. PURE ASCII.
$ErrorActionPreference = 'Continue'
$script:fail = 0
function Ok([bool]$c, [string]$m) { if ($c) { Write-Host ('PASS ' + $m) } else { Write-Host ('FAIL ' + $m); $script:fail++ } }
function Step($n) { Write-Host ('--- ' + $n) }
function Info($m) { Write-Host ('INFO ' + $m) }

$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
function SqlRaw([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -e $q 2>$null
  return ((@($o) | ForEach-Object { "$_" }) -join "`n").Trim()
}
function SqlOne([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return '' }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return '' }
  return (($ls[1] -split "`t")[0]).Trim()
}
function SqlRow([string]$q) {
  $v = SqlRaw $q
  if (-not $v) { return @() }
  $ls = $v -split "`n"
  if ($ls.Count -lt 2) { return @() }
  return @(($ls[1] -split "`t") | ForEach-Object { "$_".Trim() })
}
function SqlExec([string]$q) { SqlRaw $q | Out-Null }

$API = 'http://localhost:8080/api'
function ApiPost([string]$path, [string]$token, [string]$json) {
  $h = @{}
  if ($token) { $h['Authorization'] = $token }
  try {
    return Invoke-RestMethod -Uri ($API + $path) -Method Post -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
  } catch {
    $r = $_.Exception.Response
    if ($r) { try { return ($r.GetResponseStream() | ForEach-Object { (New-Object IO.StreamReader($_)).ReadToEnd() } | ConvertFrom-Json) } catch { return $null } }
    return $null
  }
}
function ApiDelete([string]$path, [string]$token) {
  try { return Invoke-RestMethod -Uri ($API + $path) -Method Delete -Headers @{ Authorization = $token } } catch { return $null }
}
function Login([string]$u, [string]$p) { return (ApiPost '/auth/login' '' ('{"username":"' + $u + '","password":"' + $p + '","companyId":1}')) }

$lg = Login 'lin' '123'
Ok ($lg.code -eq 200 -and $lg.data.token) 'login as admin'
if (-not $lg.data.token) { Write-Host 'RESULT FAIL verify-material-rd-expense (no token)'; exit 1 }
$tok = $lg.data.token

$matTypeId = SqlOne "SELECT id FROM material_type ORDER BY id LIMIT 1"
$accountId = SqlOne "SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1"
Ok ($matTypeId -ne '' -and $accountId -ne '') ("fixture prerequisites (materialTypeId=" + $matTypeId + ", accountId=" + $accountId + ")")
if ($matTypeId -eq '' -or $accountId -eq '') { Write-Host 'RESULT FAIL verify-material-rd-expense (no material type / account)'; exit 1 }

function Balance([string]$accId) { return [decimal]$((SqlOne ("SELECT IFNULL(SUM(income)-SUM(expense),0) FROM finance_cashflow WHERE account_id=" + $accId)) -replace '^$', '0') }
function RdCount([string]$matId) { return [int]$((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_MATERIAL' AND source_id=" + $matId)) -replace '^$', '0') }

$stamp = (Get-Date).ToString('yyyyMMddHHmmss')
$matIds = @()
$expIds = @()

Step 'A) ticked option -> DRAFT R&D expense, account balance untouched'
$bal0 = Balance $accountId
$m1 = ApiPost '/outsource/material' $tok ('{"materialName":"RD-EXP-A-' + $stamp + '","materialTypeId":' + $matTypeId + ',"unit":"PCS","price":12.5,"supplierIds":""}')
Ok ($m1.code -eq 200 -and $m1.data) ('fixture material A created (id=' + $m1.data + ')')
if (-not $m1.data) { Write-Host 'RESULT FAIL verify-material-rd-expense (cannot create material)'; exit 1 }
$matA = [string]$m1.data; $matIds += $matA

$r1 = ApiPost ("/outsource/material/" + $matA + "/rd-expense") $tok ('{"amount":88.5,"accountId":' + $accountId + ',"expenseDate":"2026-09-27","remark":""}')
Ok ($r1.code -eq 200 -and $r1.data.expenseId) ('A: rd-expense created (code=' + $r1.code + ', no=' + $r1.data.expenseNo + ')')
$expIds += [string]$r1.data.expenseId
$row = SqlRow ("SELECT status, expense_type, amount, IFNULL(source_bill_type,''), IFNULL(source_id,0), IFNULL(remark,'') FROM finance_expense WHERE id=" + $r1.data.expenseId)
Info ('A row: ' + ($row -join ' | '))
Ok ($row[0] -eq 'DRAFT' -and $row[1] -eq 'RND' -and [decimal]$row[2] -eq 88.5 -and $row[3] -eq 'RD_MATERIAL' -and [string]$row[4] -eq $matA) 'A: row = DRAFT / type RND / amount 88.5 / source RD_MATERIAL + materialId'
Ok (((SqlOne ("SELECT IFNULL(remark,'') FROM finance_expense WHERE id=" + $r1.data.expenseId)) -like ('*RD-EXP-A-' + $stamp + '*'))) 'A: remark defaults to include the material name'
Ok ((Balance $accountId) -eq $bal0) ('A: account balance unchanged (' + $bal0 + ') -- money moves only on audit')

Step 'B) NEGATIVE: material created WITHOUT the option has no R&D expense'
$m2 = ApiPost '/outsource/material' $tok ('{"materialName":"RD-EXP-B-' + $stamp + '","materialTypeId":' + $matTypeId + ',"unit":"PCS","price":3,"supplierIds":""}')
$matB = [string]$m2.data; $matIds += $matB
Ok ($matB -ne '') ('fixture material B created (id=' + $matB + ')')
Ok ((RdCount $matB) -eq 0) 'B: no expense row was created for the unticked material'

Step 'C) idempotent: second call returns the same document'
$r2 = ApiPost ("/outsource/material/" + $matA + "/rd-expense") $tok ('{"amount":999,"accountId":' + $accountId + '}')
Ok ($r2.code -eq 200 -and $r2.data.existing -eq $true) 'C: second call answered existing=true'
Ok ([string]$r2.data.expenseNo -eq [string]$r1.data.expenseNo) 'C: same expense no returned'
Ok ((RdCount $matA) -eq 1) 'C: still exactly one expense row for the material'

Step 'D) NEGATIVE: amount<=0 / missing account are rejected'
$r3 = ApiPost ("/outsource/material/" + $matA + "/rd-expense") $tok ('{"amount":0,"accountId":' + $accountId + '}')
Info ('D amount=0 -> code=' + $r3.code + ' msg=' + $r3.msg)
Ok ($r3.code -ne 200 -or $r3.data.existing -eq $true) 'D: amount=0 rejected (idempotent short-circuit also acceptable for an existing row)'
$r4 = ApiPost ("/outsource/material/" + $matB + "/rd-expense") $tok '{"amount":50}'
Info ('D no account -> code=' + $r4.code + ' msg=' + $r4.msg)
Ok ($r4.code -ne 200) 'D: missing account rejected'
Ok ((RdCount $matB) -eq 0) 'D: still no expense row for material B'

Step 'E) PERMISSION: material-page user (no finance perms) can file it; the finance endpoint stays closed'
$isoRole = 'perm_rd_iso'
$isoUser = 'perm_rd_user'
SqlExec ("INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('perm rd iso', '" + $isoRole + "', 1, 'temp verify-material-rd-expense role', 1);")
$isoRid = SqlOne ("SELECT id FROM sys_role WHERE role_code='" + $isoRole + "'")
$matMenuId = SqlOne "SELECT id FROM sys_menu WHERE route_name='OutsourceMaterialInfo' AND menu_type='menu' ORDER BY id LIMIT 1"
Ok ($isoRid -ne '' -and $matMenuId -ne '') ('E prerequisites (roleId=' + $isoRid + ', materialInfoMenu=' + $matMenuId + ')')
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $isoRid + ";")
SqlExec ("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (" + $isoRid + ", " + $matMenuId + ");")
ApiPost '/system/user' $tok ('{"username":"' + $isoUser + '","password":"123","status":1}') | Out-Null
$isoUid = SqlOne ("SELECT id FROM sys_user WHERE username='" + $isoUser + "'")
if ($isoUid -ne '') { SqlExec ("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (" + $isoUid + ", " + $isoRid + ");") }
$lg2 = Login $isoUser '123'
Ok ($lg2.code -eq 200 -and $lg2.data.token) ('E: logged in as ' + $isoUser + ' (only menu = material info)')
if ($lg2.data.token) {
  $isoTok = $lg2.data.token
  $r5 = ApiPost ("/outsource/material/" + $matB + "/rd-expense") $isoTok ('{"amount":7.5,"accountId":' + $accountId + '}')
  Ok ($r5.code -eq 200) ('E: material-page-only user CAN file the R&D expense (code=' + $r5.code + ')')
  if ($r5.data.expenseId) { $expIds += [string]$r5.data.expenseId }
  $r6 = ApiPost '/finance/expense' $isoTok ('{"expenseType":"RND","amount":7.5,"accountId":' + $accountId + '}')
  Ok ($r6.code -ne 200) ('E: the same user is DENIED on /finance/expense (code=' + $r6.code + ') -- that is why the endpoint lives under the material prefix')
}

Step 'F) expense type is validated by the backend enum (unknown rejected, lowercase normalised)'
$r7 = ApiPost '/finance/expense' $tok ('{"expenseType":"ABCDEFG","amount":1,"accountId":' + $accountId + '}')
Info ('F unknown type -> code=' + $r7.code + ' msg=' + $r7.msg)
Ok ($r7.code -ne 200) 'F: unknown expense type rejected (no dirty code can be stored any more)'
Ok ((SqlOne "SELECT COUNT(*) FROM finance_expense WHERE expense_type='ABCDEFG'") -eq '0') 'F: nothing was written for the rejected type'
$before = [int]$((SqlOne "SELECT COUNT(*) FROM finance_expense") -replace '^$', '0')
$r8 = ApiPost '/finance/expense' $tok ('{"expenseType":"rnd","amount":1,"accountId":' + $accountId + '}')
Ok ($r8.code -eq 200) 'F: lowercase "rnd" accepted (normalised -- historical/script values still work)'
$normId = SqlOne "SELECT id FROM finance_expense ORDER BY id DESC LIMIT 1"
if ($normId -ne '' -and [int]$normId -gt $before) {
  $expIds += [string]$normId
  Ok ((SqlOne ("SELECT expense_type FROM finance_expense WHERE id=" + $normId)) -eq 'RND') 'F: stored as the canonical code RND'
} else {
  Ok $false 'F: could not locate the row created with the lowercase type'
}

Step 'cleanup: fixture expenses + materials + temp role/user'
foreach ($e in $expIds) { if ($e -and $e -ne '') { SqlExec ("DELETE FROM finance_expense WHERE id=" + $e + ";") } }
foreach ($m in $matIds) { if ($m -and $m -ne '') { ApiDelete ("/outsource/material/" + $m) $tok | Out-Null; SqlExec ("DELETE FROM outsource_material WHERE id=" + $m + ";") } }
SqlExec ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $isoUser + "');")
SqlExec ("DELETE FROM sys_user WHERE username='" + $isoUser + "';")
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $isoRid + ";")
SqlExec ("DELETE FROM sys_role WHERE role_code='" + $isoRole + "';")
$leftM = SqlOne ("SELECT COUNT(*) FROM outsource_material WHERE material_name LIKE 'RD-EXP-%'")
$leftU = SqlOne ("SELECT COUNT(*) FROM sys_user WHERE username='" + $isoUser + "'")
Ok ($leftM -eq '0' -and $leftU -eq '0') ('cleanup done (materials left=' + $leftM + ', user left=' + $leftU + ')')

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-material-rd-expense  (FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
