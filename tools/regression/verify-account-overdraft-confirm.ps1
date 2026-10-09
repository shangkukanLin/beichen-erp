# 账户余额不足「提示 + 用户确认后可通过」守卫（2026-10-09 用户口径，报告 §7.29）。
#
# 口径：扣款时余额不足 ⇒ **先给用户提示**；用户确认后**可通过**（账户可被扣成负数）。覆盖两条支出路径：
#   费用单（研发支出的自动审核复用同一个 audit）与付款单（多账户逐行校验）。
#
# 钉住四件事（全部 HTTP + DB 事实，不开浏览器；夹具用完即删）：
#   ① 未确认 ⇒ 业务码 **409**，且**一分钱没动**（单据仍草稿 / 账户余额未变 / 不写支出流水）。
#      这是本方案的安全基石：抛错发生在任何写库之前 ⇒ "非 200"就一定没动账（脚本不会把"没执行"误当成功）。
#   ② 带 allowOverdraft=true（= 用户点了「确认继续」）⇒ 通过，账户**真的被扣成负数**，资金流水备注留痕（可查）。
#   ③ 负例：余额充足时**不带标志照样通过** ⇒ 证明本改动只放宽了"余额不足"这一种情况，别的行为没变。
#   ④ 收尾反审核 ⇒ 冲回、账户复原 ⇒ "确认透支行"同样可逆，不留对不上的账。
#
# ⚠️ $ErrorActionPreference 必须 Continue —— mysql CLI 往 stderr 写密码告警。
# ⚠️ 变量不要叫 $pid（PowerShell 只读自动变量，赋值被拒后断言会拿进程号去比，假红）。
$ErrorActionPreference = 'Continue'

$API = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$ACC_NAME = 'E2E-OD-ACC'
$TAG = 'E2E-OVERDRAFT'
$script:PASS = 0; $script:FAIL = 0; $script:SKIP = 0
function Ok([bool]$c, [string]$m) { if ($c) { $script:PASS++; Write-Host ('PASS ' + $m) } else { $script:FAIL++; Write-Host ('FAIL ' + $m) } }
function Skip([string]$m) { $script:SKIP++; Write-Host ('SKIP ' + $m) }
function Summary([string]$t) {
  $tot = $script:PASS + $script:FAIL
  if ($tot -eq 0) { Write-Host ('RESULT SKIP ' + $t + ' (PASS=0 FAIL=0 SKIP=' + $script:SKIP + ')'); return }
  Write-Host ('RESULT ' + $(if ($script:FAIL -eq 0) { 'PASS' } else { 'FAIL' }) + ' ' + $t + '  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ' SKIP=' + $script:SKIP + ')')
  if ($script:FAIL -gt 0) { try { $Host.SetShouldExit(1) } catch { } }
}
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $q 2>$null
  return (@($o) | ForEach-Object { "$_" } | Select-Object -First 1)
}
function Bal([long]$accId) { return [decimal](SqlOne "SELECT IFNULL(SUM(income-expense),0) FROM finance_cashflow WHERE account_id=$accId") }

# ---------- 登录 ----------
try {
  $lr = Invoke-RestMethod -Uri "$API/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = 'lin'; password = '123'; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
} catch { Write-Host ('FAIL login: ' + $_.Exception.Message); Summary 'account overdraft confirm (7.29)'; exit 1 }
$H = @{ Authorization = $lr.data.token }
function Api([string]$method, [string]$path, $body) {
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($API + $path) -Method $method -Headers $H -TimeoutSec 30 }
    return Invoke-RestMethod -Uri ($API + $path) -Method $method -Headers $H -ContentType 'application/json; charset=utf-8' `
      -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8))) -TimeoutSec 30
  } catch { return $null }
}
$today = (Get-Date).ToString('yyyy-MM-dd')

# ---------- 夹具：一个只有 100 元期初的账户（余额可控，便于造"余额不足"） ----------
$null = Api 'POST' '/finance/account' @{ accountName = $ACC_NAME; accountType = 'CASH'; openingBalance = 100; status = 1 }
$ACC = [long](SqlOne "SELECT id FROM finance_account WHERE account_name='$ACC_NAME' ORDER BY id DESC LIMIT 1")
if (-not $ACC -or $ACC -le 0) { Write-Host 'FAIL cannot create the fixture account'; Summary 'account overdraft confirm (7.29)'; exit 1 }
$bal0 = Bal $ACC
Write-Host ('  fixture account #' + $ACC + ' balance=' + $bal0)
Ok ($bal0 -eq 100) 'fixture account starts with a 100.0000 balance (opening flow)'

# =====================================================================================
Write-Host '--- (1) EXPENSE: short balance must be REFUSED with code 409 and move NO money'
$e1 = Api 'POST' '/finance/expense' @{ expenseType = 'OFFICE'; amount = 150; accountId = $ACC; expenseDate = $today; remark = $TAG + '-EXP' }
$e1id = [long](SqlOne "SELECT id FROM finance_expense WHERE remark='$TAG-EXP' ORDER BY id DESC LIMIT 1")
$e1no = SqlOne ("SELECT expense_no FROM finance_expense WHERE id=" + $e1id)
Write-Host ('  expense #' + $e1id + ' no=' + $e1no + ' create.code=' + $e1.code)
Ok (-not [string]::IsNullOrWhiteSpace($e1no)) 'a 150.0000 expense draft was created against the 100.0000 account'
$r1 = Api 'PUT' ("/finance/expense/$e1id/audit") $null
Write-Host ('  audit WITHOUT confirm -> code=' + $r1.code + ' msg=' + $r1.msg)
Ok ($r1.code -eq 409) 'auditing it without confirmation answers the dedicated business code 409'
Ok ($r1.msg -match '100\.0000' -and $r1.msg -match '-50\.0000') 'the message spells out the current balance and the post-deduction (negative) balance'
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE id=" + $e1id)) -eq 'DRAFT') 'and the document is STILL a draft (nothing was posted)'
Ok ((Bal $ACC) -eq 100) 'the account balance did NOT move (no money left the account)'
Ok ((SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $e1no + "'")) -eq '0') 'and not a single cashflow row was written'

Write-Host '--- (2) EXPENSE: with the user''s confirmation (allowOverdraft=true) it goes through and the account really goes negative'
$r2 = Api 'PUT' ("/finance/expense/$e1id/audit?allowOverdraft=true") $null
Write-Host ('  audit WITH confirm -> code=' + $r2.code)
Ok ($r2.code -eq 200) 'the same request with allowOverdraft=true is accepted'
Ok ((SqlOne ("SELECT status FROM finance_expense WHERE id=" + $e1id)) -eq 'AUDITED') 'the document is now AUDITED'
Ok ((Bal $ACC) -eq -50) 'the account really went negative (100.0000 - 150.0000 = -50.0000)'
$note = SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $e1no + "' AND remark LIKE '%100.0000%' AND remark LIKE '%-50.0000%'")
Ok ("$note" -eq '1') 'the cashflow note kept a trace (before/after balances inside the remark) - auditable'

Write-Host '--- (3) EXPENSE: reversing the overdraft restores the balance (the confirmed overdraft is reversible)'
$r3 = Api 'PUT' ("/finance/expense/$e1id/un-audit") $null
Ok ($r3.code -eq 200 -and (Bal $ACC) -eq 100) 'un-audit brings the account back to 100.0000'

Write-Host '--- (4) NEGATIVE CONTROL: a sufficient balance passes even WITHOUT the flag (nothing else changed)'
$e2 = Api 'POST' '/finance/expense' @{ expenseType = 'OFFICE'; amount = 50; accountId = $ACC; expenseDate = $today; remark = $TAG + '-EXP-OK' }
$e2id = [long](SqlOne "SELECT id FROM finance_expense WHERE remark='$TAG-EXP-OK' ORDER BY id DESC LIMIT 1")
$r4 = Api 'PUT' ("/finance/expense/$e2id/audit") $null
Ok ($r4.code -eq 200 -and (Bal $ACC) -eq 50) 'a 50.0000 expense on a 100.0000 account needs no confirmation and passes'
Ok ((Api 'PUT' ("/finance/expense/$e2id/un-audit") $null).code -eq 200 -and (Bal $ACC) -eq 100) 'and its reversal also restores the balance'

# =====================================================================================
Write-Host '--- (5) PAYMENT: same two-phase behaviour on the payment audit'
$sup = [long](SqlOne 'SELECT id FROM supplier ORDER BY id LIMIT 1')
if (-not $sup -or $sup -le 0) {
  Skip 'no supplier fixture -> the payment path is not covered in this run'
} else {
  $null = Api 'POST' '/finance/payment' @{ payment = @{ supplierId = $sup; paymentDate = $today; remark = $TAG + '-PAY' }
                                           accounts = @(@{ accountId = $ACC; amount = 150 })
                                           items = @() }
  $p1id = [long](SqlOne "SELECT id FROM finance_payment WHERE remark='$TAG-PAY' ORDER BY id DESC LIMIT 1")
  $p1no = SqlOne ("SELECT code FROM finance_payment WHERE id=" + $p1id)
  Ok (-not [string]::IsNullOrWhiteSpace($p1no)) 'a 150.0000 payment draft was created against the 100.0000 account'
  $pr1 = Api 'PUT' ("/finance/payment/$p1id/audit") $null
  Write-Host ('  payment audit WITHOUT confirm -> code=' + $pr1.code + ' msg=' + $pr1.msg)
  Ok ($pr1.code -eq 409) 'payment audit without confirmation answers 409 as well'
  Ok ((SqlOne ("SELECT status FROM finance_payment WHERE id=" + $p1id)) -eq 'DRAFT') 'the payment is still a draft'
  Ok ((Bal $ACC) -eq 100) 'and the account balance is untouched'
  Ok ((SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $p1no + "'")) -eq '0') 'no cashflow row was written for it'
  $pr2 = Api 'PUT' ("/finance/payment/$p1id/audit?allowOverdraft=true") $null
  Write-Host ('  payment audit WITH confirm -> code=' + $pr2.code)
  Ok ($pr2.code -eq 200) 'the confirmed payment audit is accepted'
  Ok ((SqlOne ("SELECT status FROM finance_payment WHERE id=" + $p1id)) -eq 'AUDITED') 'the payment is AUDITED'
  Ok ((Bal $ACC) -eq -50) 'and the account really went to -50.0000'
  $pnote = SqlOne ("SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no='" + $p1no + "' AND remark LIKE '%100.0000%' AND remark LIKE '%-50.0000%'")
  Ok ("$pnote" -eq '1') 'the payment cashflow keeps the same auditable note'
  Ok ((Api 'PUT' ("/finance/payment/$p1id/un-audit") $null).code -eq 200 -and (Bal $ACC) -eq 100) 'un-auditing the payment restores the balance too'
}

# =====================================================================================
Write-Host '--- (6) R&D EXPENSE: the auto-audit path forwards the confirmation (own body passthrough)'
# 研发支出「勾选即审核」复用同一个 FinanceExpenseService.audit，但它自己有**一层 body 透传**
# （asBool(b.get("allowOverdraft"))）⇒ 必须单独证一次（本仓有过"字段漏映射被静默丢弃"的先例 ✗）。
# 自建一个研发物料（BOARD = DevMaterialTypeEnum 的码），用完**连同它上面的费用单一起删**（只删自建的 ✓）。
$mnew = Api 'POST' '/dev/purchase-item' @{ name = ($TAG + '-MAT'); type = 'BOARD'; quantity = 1; amount = 10 }
$mat = "$($mnew.data.id)"
if (-not $mat -or $mat -eq '') {
  Skip 'cannot create a dev material fixture -> the passthrough is not covered in this run'
} else {
  Ok ($true) ('a dev material fixture was created (id=' + $mat + ')')
  $rd1 = Api 'POST' ("/dev/purchase-item/" + $mat + "/rd-expense") @{ amount = 150; accountId = $ACC; expenseDate = $today; remark = $TAG + '-RD'; autoAudit = $true }
  Write-Host ('  rd-expense WITHOUT confirm -> code=' + $rd1.code + ' msg=' + $rd1.msg)
  Ok ($rd1.code -eq 409) 'the R&D-expense auto-audit answers 409 when the balance is short (the default is unchanged)'
  Ok ((SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $mat)) -eq '0') 'and NOTHING was left behind (the draft rolled back together with the refused audit)'
  Ok ((Bal $ACC) -eq 100) 'the account balance is still 100.0000'
  $rd2 = Api 'POST' ("/dev/purchase-item/" + $mat + "/rd-expense") @{ amount = 150; accountId = $ACC; expenseDate = $today; remark = $TAG + '-RD'; autoAudit = $true; allowOverdraft = $true }
  Write-Host ('  rd-expense WITH confirm -> code=' + $rd2.code + ' audited=' + $rd2.data.audited)
  Ok ($rd2.code -eq 200 -and $rd2.data.audited -eq $true) 'with allowOverdraft=true in the body it pays through (the passthrough really works)'
  Ok ((Bal $ACC) -eq -50) 'and the account really went to -50.0000'
  # 收尾：冲回 → 作废 → 删掉本次产生的费用单与自建物料
  $rdExp = SqlOne ("SELECT id FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $mat + " ORDER BY id DESC LIMIT 1")
  if ($rdExp -and $rdExp -ne 'NULL') {
    $rdNo = SqlOne ("SELECT expense_no FROM finance_expense WHERE id=" + $rdExp)
    $null = Api 'PUT' ("/finance/expense/$rdExp/un-audit")
    $null = Api 'POST' ("/finance/expense/$rdExp/cancel")
    $null = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e `
      "DELETE FROM finance_cashflow WHERE related_bill_no='$rdNo'; DELETE FROM finance_expense WHERE id=$rdExp;" 2>$null
  }
  $null = Api 'DELETE' ("/dev/purchase-item/" + $mat) $null
  Write-Host ('  R&D fixture left: material=' + (SqlOne "SELECT COUNT(*) FROM dev_purchase_item WHERE id=$mat") +
              ' activeExpense=' + (SqlOne ("SELECT COUNT(*) FROM finance_expense WHERE source_bill_type='RD_DEV_MATERIAL' AND source_id=" + $mat + " AND status<>'CANCELLED'")) +
              ' ; account balance=' + (Bal $ACC))
  Ok ((Bal $ACC) -eq 100) 'and the account is back to 100.0000 after the R&D fixture was undone'
}

Write-Host '--- cleanup (fixtures only; failures here do not change the verdict)'
foreach ($t in @('EXP', 'EXP-OK')) {
  $rid = SqlOne "SELECT id FROM finance_expense WHERE remark='$TAG-$t' ORDER BY id DESC LIMIT 1"
  if ($rid -and $rid -ne 'NULL') { $null = Api 'POST' ("/finance/expense/$rid/cancel") }
}
$prow = SqlOne "SELECT id FROM finance_payment WHERE remark='$TAG-PAY' ORDER BY id DESC LIMIT 1"
if ($prow -and $prow -ne 'NULL') { $null = Api 'PUT' ("/finance/payment/$prow/cancel") }
# 夹具都是本次自己建的（remark 带 $TAG）⇒ 直接删净，别在库里留垃圾（其它守卫同款做法）。
# 顺序：流水 → 付款分款行 → 预付台账（付款未核销差额会落一张负数应付）→ 单据 → 账户。
$cleanupSql = @(
  "DELETE FROM finance_cashflow WHERE related_bill_no IN (SELECT expense_no FROM finance_expense WHERE remark LIKE '$TAG%')",
  "DELETE FROM finance_cashflow WHERE related_bill_no IN (SELECT code FROM finance_payment WHERE remark LIKE '$TAG%')",
  "DELETE FROM finance_cashflow WHERE account_id=$ACC",
  "DELETE FROM finance_payment_account WHERE payment_id IN (SELECT id FROM finance_payment WHERE remark LIKE '$TAG%')",
  "DELETE FROM finance_payable WHERE source_bill_type='ADVANCE_LEDGER' AND source_id IN (SELECT id FROM finance_payment WHERE remark LIKE '$TAG%')",
  "DELETE FROM finance_expense WHERE remark LIKE '$TAG%'",
  "DELETE FROM finance_payment WHERE remark LIKE '$TAG%'",
  "DELETE FROM finance_account WHERE id=$ACC"
) -join '; '
$null = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D beichen_erp -N -B -e $cleanupSql 2>$null
Write-Host ('  fixture rows left: account=' + (SqlOne "SELECT COUNT(*) FROM finance_account WHERE account_name='$ACC_NAME'") +
            ' expense=' + (SqlOne "SELECT COUNT(*) FROM finance_expense WHERE remark LIKE '$TAG%'") +
            ' payment=' + (SqlOne "SELECT COUNT(*) FROM finance_payment WHERE remark LIKE '$TAG%'") +
            ' cashflow=' + (SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE account_id=$ACC"))

Summary 'account balance overdraft: prompt first (409, no money moved), confirm to proceed (negative balance + audit note)'
