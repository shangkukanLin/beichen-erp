# Batch C fix verification (2026-09-29): F7-226 / F7-229 / F7-230 / F7-231 / F7-232.
# ASCII ONLY (no BOM -> PS 5.1 reads non-ASCII as GBK). Chinese needles are built from code points.
# Design: every check is a NEGATIVE (must be rejected) or a permission probe that cannot mutate data
# (`PUT /api/finance/account` with a non-existent id = no-op), plus the account status flip which is restored.
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
$CN_NONAME  = Cn @(0x8D26,0x6237,0x540D,0x79F0,0x4E0D,0x80FD,0x4E3A,0x7A7A)                          # 账户名称不能为空
$CN_NEGOPEN = Cn @(0x671F,0x521D,0x4F59,0x989D,0x4E0D,0x80FD,0x4E3A,0x8D1F,0x6570)                  # 期初余额不能为负数
$CN_DISABLED= Cn @(0x5DF2,0x505C,0x7528)                                                            # 已停用
$CN_NOACCT  = Cn @(0x652F,0x51FA,0x8D26,0x6237,0x4E0D,0x80FD,0x4E3A,0x7A7A)                        # 支出账户不能为空
$CN_LOSSACC = Cn @(0x62A5,0x635F,0x635F,0x5931,0x4E3A,0x975E,0x8D44,0x91D1,0x8D39,0x7528)           # 报损损失为非资金费用
$CN_NOPERM  = Cn @(0x65E0,0x6743,0x9650)                                                            # 无权限
$CN_EXISTS  = Cn @(0x5DF2,0x5B58,0x5728)                                                            # 已存在
$CN_ROLE    = Cn @(0x5BA1,0x8BA1,0x2D,0x4FEE,0x6539,0x5B88,0x536B)                                  # 审计-修改守卫
$TODAY = '2026-09-29'

function ApiAs($tok, $method, $path, [string]$json) {
  try {
    if ($json) { return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers @{ Authorization = $tok } -ContentType 'application/json' -Body $json }
    return Invoke-RestMethod -Uri "$base$path" -Method $method -Headers @{ Authorization = $tok }
  } catch { return $_.ErrorDetails.Message }
}
$lgL = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$hLin = @{ Authorization = $lgL.data.token }
function ApiLin($method, $path, [string]$json) { return (ApiAs $hLin.Authorization $method $path $json) }

# ---------- fixtures ----------
$acct = SqlOne "SELECT id FROM finance_account WHERE status=1 ORDER BY id LIMIT 1"
$acctCnt = SqlOne 'SELECT COUNT(*) FROM finance_account'
$expCnt  = SqlOne 'SELECT COUNT(*) FROM finance_expense'
Write-Host ('[SEED] account=' + $acct + ' accounts=' + $acctCnt + ' expenses=' + $expCnt)
Ok ($acct -ne '') 'an active account is available'

$roleId = ''; $userId = ''
try {
  Step '1) F7-229: opening an account must validate name and opening balance'
  $r = ApiLin 'Post' '/finance/account' '{"accountName":"","accountType":"bank","openingBalance":0}'
  Write-Host ('  blank name -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NONAME)) 'blank account name is rejected'
  $r = ApiLin 'Post' '/finance/account' '{"accountName":"AUDIT-F7-229","accountType":"bank","openingBalance":-1}'
  Write-Host ('  negative opening -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NEGOPEN)) 'negative opening balance is rejected'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_account') -eq $acctCnt) 'no account row was created by either attempt'

  Step '1b) D-14: duplicate account names are refused on create AND on rename (uk_account_name)'
  # CASH-01 is an ASCII name, so the JSON body needs no UTF-8 gymnastics.
  $r = ApiLin 'Post' '/finance/account' '{"accountName":"CASH-01","accountType":"bank","openingBalance":0}'
  Write-Host ('  duplicate name on create -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_EXISTS)) 'creating an account with an existing name is rejected'
  $other = SqlOne 'SELECT id FROM finance_account WHERE account_name <> ''CASH-01'' ORDER BY id LIMIT 1'
  $r = ApiLin 'Put' '/finance/account' ('{"id":' + $other + ',"accountName":"CASH-01"}')
  Write-Host ('  rename onto an existing name -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_EXISTS)) 'renaming an account onto an existing name is rejected'
  Ok ((SqlOne ('SELECT account_name FROM finance_account WHERE id=' + $other)) -ne 'CASH-01') 'the rejected rename did not touch the row'
  # positive control: renaming an account to ITS OWN name is allowed (the id-exclusion works) -> no-op
  $own = SqlOne ('SELECT account_name FROM finance_account WHERE id=' + $other)
  $r = ApiLin 'Put' '/finance/account' ('{"id":' + $other + ',"accountName":"' + $own + '"}')
  Ok ((("$r") -notmatch [regex]::Escape($CN_EXISTS)) -and (("$r") -notmatch '403')) 'renaming to its own name is still allowed'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_account') -eq $acctCnt) 'still no account row created'

  Step '2) F7-230: a disabled account must not be usable for an expense'
  Run ("UPDATE finance_account SET status=0 WHERE id=" + $acct)
  $body = '{"expenseType":"OFFICE","amount":1,"expenseDate":"' + $TODAY + '","accountId":' + $acct + ',"remark":"AUDIT-F7-230"}'
  $r = ApiLin 'Post' '/finance/expense' $body
  Write-Host ('  create with disabled account -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_DISABLED)) 'creating an expense on a disabled account is rejected'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_expense') -eq $expCnt) 'no expense row was created'
  Run ("UPDATE finance_account SET status=1 WHERE id=" + $acct)
  Ok ((SqlOne ("SELECT status FROM finance_account WHERE id=" + $acct)) -eq '1') 'account status restored to 1'

  Step '3) F7-231: the manual entry point must not accept a client-supplied source (non-cash bypass)'
  $body = '{"expenseType":"OFFICE","amount":1,"expenseDate":"' + $TODAY + '","accountId":null,"sourceBillType":"RD_MATERIAL","remark":"AUDIT-F7-231"}'
  $r = ApiLin 'Post' '/finance/expense' $body
  Write-Host ('  no account + forged source -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_NOACCT)) 'a forged source does not make the expense "non-cash"'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_expense') -eq $expCnt) 'no expense row was created'

  Step '4) F7-232: LOSS (non-cash by meaning) must not carry an account'
  $body = '{"expenseType":"LOSS","amount":1,"expenseDate":"' + $TODAY + '","accountId":' + $acct + ',"remark":"AUDIT-F7-232"}'
  $r = ApiLin 'Post' '/finance/expense' $body
  Write-Host ('  LOSS with account -> ' + "$r")
  Ok (("$r") -match [regex]::Escape($CN_LOSSACC)) 'LOSS with an account is rejected'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_expense') -eq $expCnt) 'no expense row was created'

  Step '5) F7-226: PUT (modify) must need finance:account, POST (inline create) stays open to business pages'
  # temporary user holding ONLY sale:order (a code that legitimately inlines account creation)
  $menuId = SqlOne "SELECT id FROM sys_menu WHERE perms='sale:order' LIMIT 1"
  $hash = SqlOne "SELECT password FROM sys_user WHERE username='lin'"
  $ROLE = 'AUDIT_SALE_ORDER'; $USER = 'audit_perm_sale'
  Run ("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='" + $ROLE + "')")
  Run ("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username='" + $USER + "')")
  Run ("DELETE FROM sys_role WHERE role_code='" + $ROLE + "'"); Run ("DELETE FROM sys_user WHERE username='" + $USER + "'")
  Run ("INSERT INTO sys_role (role_name, role_code, status, remark, company_id) VALUES ('" + $CN_ROLE + "','" + $ROLE + "',1,'2026-09-29 audit fixture, safe to delete',1)")
  $roleId = SqlOne ("SELECT id FROM sys_role WHERE role_code='" + $ROLE + "'")
  Run ("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (" + $roleId + "," + $menuId + ")")
  Run ("INSERT INTO sys_user (username, password, status, company_id, deleted, menu_mode) VALUES ('" + $USER + "','" + $hash + "',1,1,0,'ROLE')")
  $userId = SqlOne ("SELECT id FROM sys_user WHERE username='" + $USER + "'")
  Run ("INSERT INTO sys_user_role (user_id, role_id) VALUES (" + $userId + "," + $roleId + ")")
  $lgS = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body ('{"username":"' + $USER + '","password":"123","companyId":1}')
  $tokS = $lgS.data.token
  Ok ([bool]$tokS) 'sale:order-only user logged in'
  # POST: reaches the business layer (invalid body => business error, NOT 403)  -> inline-create still allowed
  $r = ApiAs $tokS 'Post' '/finance/account' '{"accountName":"","accountType":"bank","openingBalance":0}'
  Write-Host ('  sale:order POST /finance/account -> ' + "$r")
  Ok ((("$r") -notmatch [regex]::Escape($CN_NOPERM)) -and (("$r") -notmatch '403')) 'POST (inline create) is still allowed for the business page'
  Ok (("$r") -match [regex]::Escape($CN_NONAME)) 'and it really reached the service layer (business error returned)'
  # PUT: must be refused for sale:order (this is the F7-226 hole) -- non-existent id => nothing is modified
  $r = ApiAs $tokS 'Put' '/finance/account' '{"id":999999,"accountName":"HACKED"}'
  Write-Host ('  sale:order PUT /finance/account -> ' + "$r")
  Ok ((("$r") -match [regex]::Escape($CN_NOPERM)) -or (("$r") -match '403')) 'PUT (modify) is refused for a sale:order-only user'
  # and the same PUT passes for an account-manager (no-op on a non-existent id)
  $r = ApiLin 'Put' '/finance/account' '{"id":999999,"accountName":"NOOP"}'
  Write-Host ('  admin PUT /finance/account -> ' + "$r")
  Ok ((("$r") -notmatch [regex]::Escape($CN_NOPERM)) -and (("$r") -notmatch '403')) 'PUT is not blocked for finance:account holders'
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_account') -eq $acctCnt) 'still no account row created'
} finally {
  if ($userId -ne '') { Run ("DELETE FROM sys_user_role WHERE user_id=" + $userId); Run ("DELETE FROM sys_user WHERE id=" + $userId) }
  if ($roleId -ne '') { Run ("DELETE FROM sys_role_menu WHERE role_id=" + $roleId); Run ("DELETE FROM sys_role WHERE id=" + $roleId) }
  Run ("UPDATE finance_account SET status=1 WHERE id=" + $acct)
  Write-Host ('[CLEANUP] temp role/user removed; account status=1 ; accounts=' + (SqlOne 'SELECT COUNT(*) FROM finance_account') + ' expenses=' + (SqlOne 'SELECT COUNT(*) FROM finance_expense'))
  Ok ((SqlOne 'SELECT COUNT(*) FROM finance_expense') -eq $expCnt) 'no expense row left behind'
}
Write-Host ('RESULT fix F7-226/229/230/231/232 PASS=' + $script:PASS + ' FAIL=' + $script:FAIL)
