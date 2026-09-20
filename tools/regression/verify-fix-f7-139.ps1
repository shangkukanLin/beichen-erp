# verify-fix-f7-139.ps1
#
# F7-139 regression (2026-09-20): the remaining four "no atomic guard" flows from the static cross-analysis
# (checklist A) were closed with row locks / a symmetric CAS:
#   1) FinanceBillServiceImpl.generate      -> lock the partner row (customer / supplier) inside the
#      JVM mutex, so the "check-then-insert" idempotency guard also holds ACROSS instances
#      (its own javadoc admitted "single-JVM mutex only; needs a DB constraint").
#   2) ProjectProductSyncServiceImpl.syncProduct -> lock the project row (its "find product by projectId,
#      create when absent" guard was a plain non-atomic selectOne => the same project could get two products).
#   3) ProjectServiceImpl.reactivate        -> make it a *symmetric* CAS with cancel() (it used to be an
#      unconditional update that could silently overwrite CANCELLED and clear cancelled_at).
#   4) ProjectPhaseServiceImpl (7 entry points) -> lock the project row so all phase operations of one
#      project serialise (the whole phase state machine had no CAS at all).
#
# What is asserted:
#   * the two NEW annotated FOR UPDATE queries actually parse and run (runtime probe with a non-existent id
#     -- an annotated SQL error only shows up at runtime, never at compile time);
#   * reactivate() now refuses a project that is not CANCELLED (behaviour change, negative case);
#   * three CONCURRENT generate() calls for the same (type, partner, period) still yield exactly one bill.
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

$CUSTOMER = 15
$PERIOD_END = '2027-01-31'   # far future: avoids colliding with any existing bill
$PROJECT = 10                # IN_PROGRESS project (reactivate must refuse it)

Write-Output '=== 0) login + baseline ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

$bBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill')
$biBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill_item')
$bMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_bill')
Info "baseline: finance_bill=$bBase items=$biBase maxId=$bMax"
# clear any leftover from a previous aborted run of THIS script (same key), then re-read the baseline
SqlExec ("DELETE FROM finance_bill_item WHERE bill_id IN (SELECT id FROM finance_bill WHERE bill_type='RECEIVABLE' AND partner_id=$CUSTOMER AND period_end='$PERIOD_END')")
SqlExec ("DELETE FROM finance_bill WHERE bill_type='RECEIVABLE' AND partner_id=$CUSTOMER AND period_end='$PERIOD_END'")
$bBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill')
$biBase = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill_item')
$bMax = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM finance_bill')
Info "after pre-clean: finance_bill=$bBase items=$biBase maxId=$bMax"

# ---------- 1) the new FOR UPDATE queries must PARSE AND RUN ----------
Write-Output ''
Write-Output '=== 1) runtime probes for the new annotated FOR UPDATE queries ==='
$genBody = @{ billType = 'RECEIVABLE'; partnerId = 999999; partnerName = 'VERIFY-F7139'; periodEnd = $PERIOD_END }
$rA = Req 'POST' '/finance/bill/generate' $admin $genBody
if ((BMsg $rA) -like '*客户不存在*') { Ok 'CustomerMapper.selectForUpdate ran and answered a business error (客户不存在)' }
elseif ((BMsg $rA) -match 'Error|SQL|syntax|Exception|MyBatis') { Bad ("CustomerMapper.selectForUpdate -> DATABASE ERROR: " + (BMsg $rA)) }
else { Bad ('CustomerMapper probe: unexpected answer -> ' + (BMsg $rA)) }

$genBody2 = @{ billType = 'PAYABLE'; partnerId = 999999; partnerName = 'VERIFY-F7139'; periodEnd = $PERIOD_END }
$rB = Req 'POST' '/finance/bill/generate' $admin $genBody2
if ((BMsg $rB) -like '*供应商不存在*') { Ok 'SupplierMapper.selectForUpdate (2-arg overload) ran and answered a business error (供应商不存在)' }
elseif ((BMsg $rB) -match 'Error|SQL|syntax|Exception|MyBatis') { Bad ("SupplierMapper.selectForUpdate(2) -> DATABASE ERROR: " + (BMsg $rB)) }
else { Bad ('SupplierMapper probe: unexpected answer -> ' + (BMsg $rB)) }

$rC = Req 'PUT' '/dev/project/phase/recalc?projectId=999999' $admin $null
if ((BMsg $rC) -match 'Error|SQL|syntax|Exception|MyBatis') { Bad ("ProjectMapper.selectForUpdate -> DATABASE ERROR: " + (BMsg $rC)) }
else { Ok ('ProjectMapper.selectForUpdate ran without a database error (answer: code=' + (BCode $rC) + ' msg=' + (BMsg $rC) + ')') }

# ---------- 2) reactivate() must refuse a project that is not CANCELLED ----------
Write-Output ''
Write-Output '=== 2) reactivate() is now a CAS: a non-CANCELLED project must be refused ==='
$pStatus = SqlOne "SELECT status FROM dev_project WHERE id=$PROJECT"
if ($pStatus -eq '') { Skip "project $PROJECT not found" }
elseif ($pStatus -eq 'CANCELLED') { Info "project $PROJECT is CANCELLED - skipping the negative case" }
else {
  $rR = Req 'PUT' ("/dev/project/" + $PROJECT + "/reactivate") $admin $null
  if ((BCode $rR) -eq 200) { Bad ("reactivate ACCEPTED a project whose status is $pStatus (CAS not applied)") }
  elseif ((BMsg $rR) -like '*只有已取消的项目*') { Ok ('reactivate refused the ' + $pStatus + ' project: ' + (BMsg $rR)) }
  else { Bad ('reactivate refused with an unexpected message: ' + (BMsg $rR)) }
  $pAfter = SqlOne "SELECT status FROM dev_project WHERE id=$PROJECT"
  if ($pAfter -eq $pStatus) { Ok "project $PROJECT status unchanged ($pAfter) - the refusal rolled back" }
  else { Bad "project $PROJECT status changed to $pAfter by a refused call" }
}

# ---------- 3) concurrent generate() for the same key must still yield exactly one bill ----------
Write-Output ''
Write-Output '=== 3) concurrent generate() x3 (same type/partner/period) -> exactly one bill ==='
$startAt = (Get-Date).AddSeconds(4).ToString('o')
$jobs = @()
foreach ($i in 1..3) {
  $jobs += Start-Job -ScriptBlock {
    param($api, $tok, $customer, $periodEnd, $runAt)
    $h = @{ Authorization = $tok }
    $b = @{ billType = 'RECEIVABLE'; partnerId = [long]$customer; partnerName = 'VERIFY-F7139'; periodEnd = $periodEnd } | ConvertTo-Json
    while ((Get-Date) -lt [datetime]::Parse($runAt)) { Start-Sleep -Milliseconds 20 }
    try {
      $r = Invoke-RestMethod -Uri "$api/finance/bill/generate" -Method Post -Headers $h `
           -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 60
      return @{ code = [int]$r.code; msg = [string]$r.msg }
    } catch {
      $sc = -1; try { $sc = [int]$_.Exception.Response.StatusCode.value__ } catch { }
      $msg = 'transport-error'
      try {
        $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $txt = $sr.ReadToEnd()
        if ($txt) { $j = $txt | ConvertFrom-Json; if ($j.msg) { $msg = [string]$j.msg } }
      } catch { }
      return @{ code = $sc; msg = $msg }
    }
  } -ArgumentList $B, $admin, $CUSTOMER, $PERIOD_END, $startAt
}
$jobs | Wait-Job -Timeout 180 | Out-Null
$res = @($jobs | Receive-Job)
$jobs | Remove-Job -Force -ErrorAction SilentlyContinue

$okCount = @($res | Where-Object { $_.code -eq 200 }).Count
$rejMsgs = @($res | Where-Object { $_.code -ne 200 } | ForEach-Object { $_.msg })
Info ("concurrent: 200 x$okCount, rejected x" + ($res.Count - $okCount) + " -> " + (($rejMsgs | Select-Object -First 3) -join ' | '))
$newBills = [int](SqlOne "SELECT COUNT(*) FROM finance_bill WHERE id > $bMax")
$keyBills = [int](SqlOne "SELECT COUNT(*) FROM finance_bill WHERE bill_type='RECEIVABLE' AND partner_id=$CUSTOMER AND period_end='$PERIOD_END' AND status<>'CANCELLED'")
if ($okCount -eq 1) { Ok 'exactly one of three concurrent generate() calls was accepted' }
else { Bad ("expected exactly 1 success, got $okCount") }
if ($keyBills -eq 1) { Ok ("exactly one bill exists for the key (found $keyBills) - the idempotency guard holds") }
else { Bad ("expected 1 bill for the key, found $keyBills (duplicate billing!)") }
if ($newBills -eq 1) { Ok ("only one new finance_bill row was written (id > $bMax => $newBills)") }
else { Bad ("expected 1 new bill row, got $newBills") }
$dupRej = @($rejMsgs | Where-Object { $_ -like '*已存在账单*' }).Count
if ($okCount -eq 1 -and $dupRej -ge 1) { Ok ("rejections came from the idempotency guard (''已存在账单'' x$dupRej)") }

# ---------- 4) rollback ----------
Write-Output ''
Write-Output '=== 4) rollback ==='
SqlExec ("DELETE FROM finance_bill_item WHERE bill_id > $bMax")
SqlExec ("DELETE FROM finance_bill WHERE id > $bMax")
SqlExec ("DELETE FROM finance_bill_item WHERE bill_id IN (SELECT id FROM finance_bill WHERE bill_type='RECEIVABLE' AND partner_id=$CUSTOMER AND period_end='$PERIOD_END')")
SqlExec ("DELETE FROM finance_bill WHERE bill_type='RECEIVABLE' AND partner_id=$CUSTOMER AND period_end='$PERIOD_END'")

Write-Output ''
Write-Output '=== 5) self-check ==='
$bNow = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill')
$biNow = [int](SqlOne 'SELECT COUNT(*) FROM finance_bill_item')
if ($bNow -eq $bBase -and $biNow -eq $biBase) { Ok ("counts restored: finance_bill=$bNow items=$biNow") }
else { Bad ("count mismatch: finance_bill=$bNow/$bBase items=$biNow/$biBase") }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=" + $script:skip + ")"); exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip)
exit 1
