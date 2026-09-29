# verify-material-rd-expense.ps1 (2026-09-27, user request; **2026-09-28 retargeted to DEV material**):
#   the "register an R&D expense" flow now lives on 研发物料 (/dev/material, table dev_purchase_item),
#   endpoint POST /api/dev/purchase-item/{id}/rd-expense, source type RD_DEV_MATERIAL.
#   (2026-09-28 user: the feature belongs to the R&D module's material, NOT to the outsource material page --
#    the old /outsource/material/{id}/rd-expense endpoint was removed with it.)
#
#   A) POST .../rd-expense with autoAudit=true (the "tick the box" path, 2026-09-27 user: "tick it -> auto-audit")
#      -> an AUDITED expense (type=RND, source=RD_DEV_MATERIAL/devItemId), an EXPENSE cashflow row written and the
#      ACCOUNT BALANCE REDUCED by the amount.
#   A2) the same call WITHOUT autoAudit (the list row action / after-the-fact path) -> still a DRAFT and the
#      balance must NOT move (the two entry points deliberately differ: box ticked pays now, row action waits).
#   B) NEGATIVE: a dev material created WITHOUT ticking the option must have NO R&D expense row
#      (guards against a future "create it by default" regression).
#   C) idempotent: calling again returns the SAME doc (existing=true) and must NOT deduct a second time.
#   D) NEGATIVE: amount<=0 / missing account / NOT ENOUGH BALANCE (the new failure mode auto-audit introduces)
#      -> rejected, nothing left behind, balance untouched.
#   E) GATE (option A, 2026-09-27): a user whose ONLY menu is "dev material" can still call the endpoint (200)
#      -- but the ticked box must be DOWNGRADED to a DRAFT and must NOT move money: auditing an expense needs
#      finance:expense / finance:cashflow (the codes guarding /api/finance/expense), which that user lacks
#      (posting /api/finance/expense as that user is 403). So the gate mirrors the finance endpoint.
#   E2) GATE, positive side: an account holding finance:expense + dev material pays immediately on the same call.
#
# Fixture is self-built / self-cleaned (temp dev material + temp role/user), so the file is repeatable. PURE ASCII.
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

$devMatType = 'BOARD'   # dev material types come from DevMaterialTypeEnum codes (no material_type table involved)
$accountId = SqlOne "SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1"
Ok ($accountId -ne '') ("fixture prerequisites (typeCode=" + $devMatType + ", accountId=" + $accountId + ")")
if ($accountId -eq '') { Write-Host 'RESULT FAIL verify-material-rd-expense (no account)'; exit 1 }

function Balance([string]$accId) { return [decimal]$((SqlOne ("SELECT IFNULL(SUM(income)-SUM(expense),0) FROM finance_cashflow WHERE account_id=" + $accId)) -replace '^$', '0') }
function RdCount([string]$matId) { return [int]$((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $matId)) -replace '^$', '0') }

$stamp = (Get-Date).ToString('yyyyMMddHHmmss')
$matIds = @()
$expIds = @()

Step 'A) ticked option (autoAudit=true) -> AUDITED R&D expense, account balance reduced'
$bal0 = Balance $accountId
$m1 = ApiPost '/dev/purchase-item' $tok ('{"name":"RD-EXP-A-' + $stamp + '","type":"' + $devMatType + '","quantity":1,"amount":12.5}')
Ok ($m1.code -eq 200 -and $m1.data.id) ('fixture dev material A created (id=' + $m1.data.id + ')')
if (-not $m1.data.id) { Write-Host 'RESULT FAIL verify-material-rd-expense (cannot create dev material)'; exit 1 }
$matA = [string]$m1.data.id; $matIds += $matA

$r1 = ApiPost ("/dev/purchase-item/" + $matA + "/rd-expense") $tok ('{"amount":88.5,"accountId":' + $accountId + ',"expenseDate":"2026-09-27","remark":"","autoAudit":true}')
Ok ($r1.code -eq 200 -and $r1.data.expenseId) ('A: rd-expense created (code=' + $r1.code + ', no=' + $r1.data.expenseNo + ')')
$expIds += [string]$r1.data.expenseId
Ok ($r1.data.audited -eq $true) 'A: response says audited=true (ticked path pays immediately)'
$row = SqlRow ("SELECT status, expense_type, amount, IFNULL(source_bill_type,''), IFNULL(source_id,0), IFNULL(remark,'') FROM finance_expense WHERE id=" + $r1.data.expenseId)
Info ('A row: ' + ($row -join ' | '))
Ok ($row[0] -eq 'AUDITED' -and $row[1] -eq 'RND' -and [decimal]$row[2] -eq 88.5 -and $row[3] -eq 'RD_DEV_MATERIAL' -and [string]$row[4] -eq $matA) 'A: row = AUDITED / type RND / amount 88.5 / source RD_DEV_MATERIAL + devItemId'
Ok (((SqlOne ("SELECT IFNULL(remark,'') FROM finance_expense WHERE id=" + $r1.data.expenseId)) -like ('*RD-EXP-A-' + $stamp + '*'))) 'A: remark defaults to include the material name'
$flow = SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $r1.data.expenseNo + "' AND flow_type='EXPENSE'")
Ok ($flow -eq '1') 'A: one EXPENSE cashflow row was written by the auto-audit'
Ok ((Balance $accountId) -eq ($bal0 - 88.5)) ('A: balance reduced by the amount (' + $bal0 + ' -> ' + (Balance $accountId) + ')')

Step 'A2) the row-action path (no autoAudit) still files a DRAFT and must not touch the balance'
$balA2 = Balance $accountId
$m3 = ApiPost '/dev/purchase-item' $tok ('{"name":"RD-EXP-D-' + $stamp + '","type":"' + $devMatType + '","quantity":1,"amount":5}')
$matD = [string]$m3.data.id; $matIds += $matD
Ok ($matD -ne '') ('fixture dev material D created (id=' + $matD + ')')
$r0 = ApiPost ("/dev/purchase-item/" + $matD + "/rd-expense") $tok ('{"amount":10,"accountId":' + $accountId + '}')
Ok ($r0.code -eq 200 -and $r0.data.audited -eq $false) 'A2: response says audited=false'
$expIds += [string]$r0.data.expenseId
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE id=" + $r0.data.expenseId)) -eq 'DRAFT') 'A2: row stays DRAFT (finance audits it later)'
Ok ((Balance $accountId) -eq $balA2) 'A2: balance unchanged on the draft path'

Step 'B) NEGATIVE: material created WITHOUT the option has no R&D expense'
$m2 = ApiPost '/dev/purchase-item' $tok ('{"name":"RD-EXP-B-' + $stamp + '","type":"' + $devMatType + '","quantity":1,"amount":3}')
$matB = [string]$m2.data.id; $matIds += $matB
Ok ($matB -ne '') ('fixture dev material B created (id=' + $matB + ')')
Ok ((RdCount $matB) -eq 0) 'B: no expense row was created for the unticked dev material'

Step 'C) idempotent: a second ticked call returns the SAME doc and must not deduct again'
$balC = Balance $accountId
$r2 = ApiPost ("/dev/purchase-item/" + $matA + "/rd-expense") $tok ('{"amount":999,"accountId":' + $accountId + ',"autoAudit":true}')
Ok ($r2.code -eq 200 -and $r2.data.existing -eq $true) 'C: second call answered existing=true'
Ok ([string]$r2.data.expenseNo -eq [string]$r1.data.expenseNo) 'C: same expense no returned (the 999 amount is ignored)'
Ok ($r2.data.audited -eq $true) 'C: still reported as audited'
Ok ((RdCount $matA) -eq 1) 'C: still exactly one expense row for the material'
Ok ((Balance $accountId) -eq $balC) 'C: no second deduction -- the repeat call left the balance alone'

Step 'D) NEGATIVE: amount<=0 / missing account / not enough balance are rejected'
$r3 = ApiPost ("/dev/purchase-item/" + $matA + "/rd-expense") $tok ('{"amount":0,"accountId":' + $accountId + '}')
Info ('D amount=0 -> code=' + $r3.code + ' msg=' + $r3.msg)
Ok ($r3.code -ne 200 -or $r3.data.existing -eq $true) 'D: amount=0 rejected (idempotent short-circuit also acceptable for an existing row)'
$r4 = ApiPost ("/dev/purchase-item/" + $matB + "/rd-expense") $tok '{"amount":50}'
Info ('D no account -> code=' + $r4.code + ' msg=' + $r4.msg)
Ok ($r4.code -ne 200) 'D: missing account rejected'
Ok ((RdCount $matB) -eq 0) 'D: still no expense row for material B'
# the failure mode auto-audit introduces: the balance check now decides whether the material-page call succeeds
$balD = Balance $accountId
$r4b = ApiPost ("/dev/purchase-item/" + $matB + "/rd-expense") $tok ('{"amount":99999999,"accountId":' + $accountId + ',"autoAudit":true}')
Info ('D not enough balance -> code=' + $r4b.code + ' msg=' + $r4b.msg)
Ok ($r4b.code -ne 200) 'D: auto-audit rejected when the balance cannot cover it'
Ok ((RdCount $matB) -eq 0) 'D: nothing was left behind (create + audit rolled back together)'
Ok ((Balance $accountId) -eq $balD) 'D: balance untouched by the failed auto-audit'

Step 'E) GATE (option A): a material-page user WITHOUT the finance perm gets a DRAFT, not a payment'
$isoRole = 'perm_rd_iso'
$isoUser = 'perm_rd_user'
SqlExec ("INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('perm rd iso', '" + $isoRole + "', 1, 'temp verify-material-rd-expense role', 1);")
$isoRid = SqlOne ("SELECT id FROM sys_role WHERE role_code='" + $isoRole + "'")
$matMenuId = SqlOne "SELECT id FROM sys_menu WHERE route_name='DevMaterial' AND menu_type='menu' ORDER BY id LIMIT 1"
Ok ($isoRid -ne '' -and $matMenuId -ne '') ('E prerequisites (roleId=' + $isoRid + ', devMaterialMenu=' + $matMenuId + ')')
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $isoRid + ";")
SqlExec ("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (" + $isoRid + ", " + $matMenuId + ");")
ApiPost '/system/user' $tok ('{"username":"' + $isoUser + '","password":"123","status":1}') | Out-Null
$isoUid = SqlOne ("SELECT id FROM sys_user WHERE username='" + $isoUser + "'")
if ($isoUid -ne '') { SqlExec ("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (" + $isoUid + ", " + $isoRid + ");") }
$lg2 = Login $isoUser '123'
Ok ($lg2.code -eq 200 -and $lg2.data.token) ('E: logged in as ' + $isoUser + ' (only menu = dev material)')
if ($lg2.data.token) {
  $isoTok = $lg2.data.token
  # 2026-09-27 user picked option A: the box pays immediately ONLY for users who may audit expenses
  # (finance:expense / finance:cashflow -- the codes guarding /api/finance/expense). A dev-material-only
  # account must NOT be able to move money through that page: the call is downgraded to a DRAFT.
  $m4 = ApiPost '/dev/purchase-item' $tok ('{"name":"RD-EXP-C-' + $stamp + '","type":"' + $devMatType + '","quantity":1,"amount":2}')
  $matC = [string]$m4.data.id; $matIds += $matC
  Ok ($matC -ne '') ('fixture dev material C created (id=' + $matC + ')')
  $balE = Balance $accountId
  $r5 = ApiPost ("/dev/purchase-item/" + $matC + "/rd-expense") $isoTok ('{"amount":7.5,"accountId":' + $accountId + ',"autoAudit":true}')
  Info ('E downgraded -> code=' + $r5.code + ' audited=' + $r5.data.audited + ' downgraded=' + $r5.data.downgraded)
  Ok ($r5.code -eq 200 -and $r5.data.audited -eq $false -and $r5.data.downgraded -eq $true) 'E: ticked box by a finance-less user is DOWNGRADED (audited=false, downgraded=true)'
  if ($r5.data.expenseId) { $expIds += [string]$r5.data.expenseId }
  Ok ((SqlOne ("SELECT status FROM finance_expense WHERE id=" + $r5.data.expenseId)) -eq 'DRAFT') 'E: the row is a DRAFT -- no money moved for a user who cannot audit'
  Ok ((Balance $accountId) -eq $balE) 'E: balance untouched by the downgraded call'
  $r6 = ApiPost '/finance/expense' $isoTok ('{"expenseType":"RND","amount":7.5,"accountId":' + $accountId + '}')
  Ok ($r6.code -ne 200) ('E: the same user is DENIED on /finance/expense (code=' + $r6.code + ') -- the gate mirrors that endpoint')
}

Step 'E2) GATE (option A): WITH finance:expense the very same call pays immediately'
$finRole = 'perm_rd_fin'
$finUser = 'perm_rd_user2'
$finMenuId = SqlOne "SELECT id FROM sys_menu WHERE perms='finance:expense' ORDER BY id LIMIT 1"
SqlExec ("INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('perm rd fin', '" + $finRole + "', 1, 'temp verify-material-rd-expense role 2', 1);")
$finRid = SqlOne ("SELECT id FROM sys_role WHERE role_code='" + $finRole + "'")
Ok ($finRid -ne '' -and $finMenuId -ne '') ('E2 prerequisites (roleId=' + $finRid + ', finance:expense menu=' + $finMenuId + ')')
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $finRid + ";")
SqlExec ("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (" + $finRid + ", " + $matMenuId + "), (" + $finRid + ", " + $finMenuId + ");")
ApiPost '/system/user' $tok ('{"username":"' + $finUser + '","password":"123","status":1}') | Out-Null
$finUid = SqlOne ("SELECT id FROM sys_user WHERE username='" + $finUser + "'")
if ($finUid -ne '') { SqlExec ("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (" + $finUid + ", " + $finRid + ");") }
$lg3 = Login $finUser '123'
Ok ($lg3.code -eq 200 -and $lg3.data.token) ('E2: logged in as ' + $finUser + ' (dev material + finance:expense)')
if ($lg3.data.token) {
  $m5 = ApiPost '/dev/purchase-item' $tok ('{"name":"RD-EXP-E-' + $stamp + '","type":"' + $devMatType + '","quantity":1,"amount":4}')
  $matE = [string]$m5.data.id; $matIds += $matE
  Ok ($matE -ne '') ('fixture dev material E created (id=' + $matE + ')')
  $balE2 = Balance $accountId
  $r9 = ApiPost ("/dev/purchase-item/" + $matE + "/rd-expense") $lg3.data.token ('{"amount":3.25,"accountId":' + $accountId + ',"autoAudit":true}')
  Info ('E2 paid -> code=' + $r9.code + ' audited=' + $r9.data.audited)
  Ok ($r9.code -eq 200 -and $r9.data.audited -eq $true) 'E2: with finance:expense the ticked box pays immediately'
  if ($r9.data.expenseId) { $expIds += [string]$r9.data.expenseId }
  Ok ((SqlOne ("SELECT status FROM finance_expense WHERE id=" + $r9.data.expenseId)) -eq 'AUDITED') 'E2: the row is AUDITED'
  Ok ((Balance $accountId) -eq ($balE2 - 3.25)) 'E2: balance reduced by the amount (the gate lets the payment through)'
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
foreach ($e in $expIds) {
  if (-not $e -or $e -eq '') { continue }
  # auto-audited fixture rows left an EXPENSE cashflow row behind (that row IS the balance) -> drop the flow first
  $no = SqlOne ("SELECT IFNULL(expense_no,'') FROM finance_expense WHERE id=" + $e)
  if ($no -ne '') { SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no='" + $no + "';") }
  SqlExec ("DELETE FROM finance_expense WHERE id=" + $e + ";")
}
foreach ($m in $matIds) { if ($m -and $m -ne '') { ApiDelete ("/dev/purchase-item/" + $m) $tok | Out-Null; SqlExec ("DELETE FROM dev_purchase_item WHERE id=" + $m + ";") } }
SqlExec ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $isoUser + "');")
SqlExec ("DELETE FROM sys_user WHERE username='" + $isoUser + "';")
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $isoRid + ";")
SqlExec ("DELETE FROM sys_role WHERE role_code='" + $isoRole + "';")
SqlExec ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $finUser + "');")
SqlExec ("DELETE FROM sys_user WHERE username='" + $finUser + "';")
SqlExec ("DELETE FROM sys_role_menu WHERE role_id=" + $finRid + ";")
SqlExec ("DELETE FROM sys_role WHERE role_code='" + $finRole + "';")
$leftM = SqlOne ("SELECT COUNT(*) FROM dev_purchase_item WHERE name LIKE 'RD-EXP-%'")
$leftU = SqlOne ("SELECT COUNT(*) FROM sys_user WHERE username='" + $isoUser + "' OR username='" + $finUser + "'")
Ok ($leftM -eq '0' -and $leftU -eq '0') ('cleanup done (materials left=' + $leftM + ', user left=' + $leftU + ')')

Write-Host ('RESULT ' + $(if ($script:fail -eq 0) { 'PASS' } else { 'FAIL' }) + ' verify-material-rd-expense  (FAIL=' + $script:fail + ')')
if ($script:fail -gt 0) { exit 1 }
