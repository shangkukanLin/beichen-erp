# verify-fix-f7-140.ps1
#
# F7-140 regression (2026-09-20, user decision A2 "keep the no-overdraft rule but close the hole"):
# the balance check in finance audit paths is derived from SUM(cashflow), and "read balance -> verify ->
# write cashflow" is two statements, so two CONCURRENT audits each read the same stale balance, both pass
# the check, and the account ends up NEGATIVE (overdraft). The project report had recorded this as a P3
# residual for BOTH finance_payment and finance_expense. Fixed by taking a FOR UPDATE lock on the account
# row (same family as F7-138/F7-139).
#
# This script proves it with two draft expenses against ONE account whose balance only covers one of them:
#   * before the fix both audits would pass  => final balance -4000 (overdrawn);
#   * after  the fix exactly ONE succeeds    => final balance  +8000 (never negative).
# Self-built + self-cleaned, no DDL.
#
# Needs a UTF-8 BOM (Chinese expectations).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$script:fail = 0
$script:skip = 0

function Ok($m)   { Write-Output ("PASS " + $m) }
function Bad($m)  { Write-Output ("FAIL " + $m); $script:fail++ }
function Skip($m) { Write-Output ("SKIP " + $m); $script:skip++ }
function Info($m) { Write-Output ("  [INFO] " + $m) }

$env:MYSQL_PWD = 'root'
function SqlOne([string]$q) {
  $o = & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -N -B -e $q 2>$null
  $l = @($o); if ($l.Count -lt 1) { return '' }; return ("$($l[0])").Trim()
}
function SqlExec([string]$q) { & $MYSQL --default-character-set=utf8mb4 -uroot -proot -D $DB -e $q 2>$null | Out-Null }

function Login([string]$u, [string]$p) {
  try {
    $r = Invoke-RestMethod -Uri "$B/auth/login" -Method Post -ContentType 'application/json' `
         -Body (@{ username = $u; password = $p; companyId = 1 } | ConvertTo-Json) -TimeoutSec 20
    if ($r -and $r.data -and $r.data.token) { return $r.data.token }
    return $null
  } catch { return $null }
}
function Req([string]$method, [string]$path, [string]$tok, $body) {
  $h = @{}; if ($tok) { $h['Authorization'] = $tok }
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h -TimeoutSec 40 }
    $j = ConvertTo-Json -InputObject $body -Depth 8
    return Invoke-RestMethod -Uri ($B + $path) -Method $method -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($j)) -TimeoutSec 40
  } catch {
    $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
    $msg = 'transport-error'
    try {
      $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
      $txt = $sr.ReadToEnd()
      if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
    } catch { }
    return [pscustomobject]@{ code = $sc; msg = $msg }
  }
}
function BCode($r) { if ($null -eq $r) { return -1 }; return [int]$r.code }
function BMsg($r)  { if ($null -eq $r) { return '' }; if ($null -eq $r.msg) { return '' }; return [string]$r.msg }

$ACC = 38            # WX-01, the smallest balance -> easy to make "one fits, two do not"
$TAG = 'VERIFY-F7140'

Write-Output '=== 0) login + fixture ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

$cfBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$exBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_expense')
$cfMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_cashflow')
$exMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_expense')
# clear leftovers from a previous aborted run
SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no LIKE '$TAG%'")
SqlExec ("DELETE FROM finance_expense WHERE expense_no LIKE '$TAG%'")
$cfBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$exBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_expense')
$cfMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_cashflow')
$exMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_expense')

$balBefore = [double](SqlOne "SELECT IFNULL(SUM(income-expense),0) FROM finance_cashflow WHERE account_id=$ACC")
if ($balBefore -le 0) { Skip "account $ACC has no positive balance ($balBefore) - cannot build the fixture"; Write-Output 'RESULT SKIP'; exit 0 }
# one expense fits, two do not
$amount = [math]::Round($balBefore * 0.6, 2)
Info "account $ACC balance=$balBefore, each expense=$amount (60%) => one fits, two do not"
$accName = SqlOne "SELECT account_name FROM finance_account WHERE id=$ACC"

# two draft expenses (built with SQL: the create endpoint would need more fields)
SqlExec ("INSERT INTO finance_expense (expense_no, expense_type, amount, expense_date, account_id, account_name, remark, status, company_id) VALUES ('$TAG-1', 'OTHER', $amount, CURDATE(), $ACC, '$accName', '$TAG', 'DRAFT', 1)")
SqlExec ("INSERT INTO finance_expense (expense_no, expense_type, amount, expense_date, account_id, account_name, remark, status, company_id) VALUES ('$TAG-2', 'OTHER', $amount, CURDATE(), $ACC, '$accName', '$TAG', 'DRAFT', 1)")
$e1 = [int](SqlOne "SELECT id FROM finance_expense WHERE expense_no='$TAG-1'")
$e2 = [int](SqlOne "SELECT id FROM finance_expense WHERE expense_no='$TAG-2'")
if ($e1 -lt 1 -or $e2 -lt 1) { Bad "fixture insert failed silently (e1=$e1 e2=$e2) - check column names"; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok "fixture ready: expense ids $e1 / $e2 on account $ACC"

# ---------- 1) concurrent audit: only one may pass, balance must never go negative ----------
Write-Output ''
Write-Output '=== 1) two CONCURRENT audits on the same account -> exactly one must be accepted ==='
$startAt = (Get-Date).AddSeconds(4).ToString('o')
$jobs = @()
foreach ($eid in @($e1, $e2)) {
  $jobs += Start-Job -ScriptBlock {
    param($api, $tok, $id, $runAt)
    $h = @{ Authorization = $tok }
    while ((Get-Date) -lt [datetime]::Parse($runAt)) { Start-Sleep -Milliseconds 20 }
    try {
      $r = Invoke-RestMethod -Uri "$api/finance/expense/$id/audit" -Method Put -Headers $h -TimeoutSec 60
      return @{ id = $id; code = [int]$r.code; msg = [string]$r.msg }
    } catch {
      $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
      $msg = 'transport-error'
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $txt = $sr.ReadToEnd()
        if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
      } catch { }
      return @{ id = $id; code = $sc; msg = $msg }
    }
  } -ArgumentList $B, $admin, $eid, $startAt
}
$jobs | Wait-Job -Timeout 180 | Out-Null
$res = @($jobs | Receive-Job)
$jobs | Remove-Job -Force -ErrorAction SilentlyContinue

$okCount = @($res | Where-Object { $_.code -eq 200 }).Count
$rejMsgs = @($res | Where-Object { $_.code -ne 200 } | ForEach-Object { $_.msg })
Info ("concurrent audits: 200 x$okCount, rejected x" + ($res.Count - $okCount) + " -> " + (($rejMsgs | Select-Object -First 3) -join ' | '))
if ($okCount -eq 1) { Ok 'exactly one of the two concurrent audits was accepted' }
else { Bad ("expected exactly 1 success, got $okCount") }
$insufficient = @($rejMsgs | Where-Object { $_ -like '*余额不足*' }).Count
$dupAccident = @($rejMsgs | Where-Object { $_ -like '*单号已被占用*' -or $_ -like '*Duplicate*' }).Count
if ($okCount -eq 1 -and $insufficient -ge 1) { Ok "the loser was refused by the BALANCE CHECK ('账户余额不足') => the row lock + CURRENT READ really serialised the audits" }
elseif ($dupAccident -ge 1) { Bad 'the loser was rejected by the uk_flow_no duplicate-key ACCIDENT, not by the balance check => the balance was still read from a stale snapshot (current read missing)' }
elseif ($okCount -eq 2) { Bad 'BOTH audits passed -> the account was overdrawn (the FOR UPDATE lock did not serialise)' }
else { Bad ('unexpected rejection reasons: ' + ($rejMsgs -join ' | ')) }

$balAfter = [double](SqlOne "SELECT IFNULL(SUM(income-expense),0) FROM finance_cashflow WHERE account_id=$ACC")
$newFlow = [int](SqlOne "SELECT COUNT(*) FROM finance_cashflow WHERE related_bill_no LIKE '$TAG%'")
$auditedCnt = [int](SqlOne "SELECT COUNT(*) FROM finance_expense WHERE expense_no LIKE '$TAG%' AND status='AUDITED'")
Info "after: balance=$balAfter (was $balBefore, one expense = $amount) newCashflowRows=$newFlow auditedBills=$auditedCnt"
if ($balAfter -ge 0) { Ok ("account balance stayed non-negative ($balBefore -> $balAfter) => NO overdraft") }
else { Bad ("account balance went NEGATIVE ($balBefore -> $balAfter) => overdraft happened") }
if ($newFlow -eq 1) { Ok 'exactly one cashflow row was written (no double posting)' }
else { Bad ("expected 1 new cashflow row, got $newFlow") }
if ($auditedCnt -eq 1) { Ok 'exactly one expense reached AUDITED' }
else { Bad ("expected 1 AUDITED expense, got $auditedCnt") }

# ---------- 2) sequential control: the loser can still be audited after reversing the winner ----------
Write-Output ''
Write-Output '=== 2) sequential control ==='
$rSeq = Req 'PUT' ("/finance/expense/" + $e1 + "/audit") $admin $null
if ((BCode $rSeq) -eq 200) { Bad 're-auditing an already AUDITED expense was accepted (claim should refuse it)' }
else { Ok ('re-audit refused as expected: ' + (BMsg $rSeq)) }

# ---------- 3) rollback ----------
Write-Output ''
Write-Output '=== 3) rollback ==='
SqlExec ("DELETE FROM finance_cashflow WHERE related_bill_no LIKE '$TAG%'")
SqlExec ("DELETE FROM finance_expense WHERE expense_no LIKE '$TAG%'")
SqlExec ("DELETE FROM finance_cashflow WHERE id > $cfMax AND account_id=$ACC")
SqlExec ("DELETE FROM finance_expense WHERE id > $exMax")

Write-Output ''
Write-Output '=== 4) self-check ==='
$cfNow = [int](SqlOne 'SELECT COUNT(*) FROM finance_cashflow')
$exNow = [int](SqlOne 'SELECT COUNT(*) FROM finance_expense')
$balNow = [double](SqlOne "SELECT IFNULL(SUM(income-expense),0) FROM finance_cashflow WHERE account_id=$ACC")
if ($cfNow -eq $cfBase -and $exNow -eq $exBase) { Ok ("counts restored: finance_cashflow=$cfNow finance_expense=$exNow") }
else { Bad ("count mismatch: cashflow=$cfNow/$cfBase expense=$exNow/$exBase") }
if ([math]::Abs($balNow - $balBefore) -lt 0.01) { Ok ("account $ACC balance restored: $balNow (was $balBefore)") }
else { Bad ("account balance NOT restored: $balNow (was $balBefore)") }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=" + $script:skip + ")"); exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip)
exit 1
