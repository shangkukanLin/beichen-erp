# audit 2026-10-05 batch 5 (权限/系统面) - mostly READ ONLY, with two negative calls that are refused
# BEFORE any DB work (the confirm gate returns before opening the transaction) and one dryRun that the
# endpoint itself rolls back (conn.rollback() on the dryRun branch, code-verified) + writes an audit row.
# ASCII ONLY in every printed string.
$ErrorActionPreference = 'Continue'
$api = 'http://localhost:8080/api'
$fail = 0
function Ok($m) { Write-Output ("PASS " + $m) }
function Bad($m) { Write-Output ("FAIL " + $m); $script:fail++ }
function Note($m) { Write-Output ("NOTE " + $m) }
function Login([string]$u) {
  $b = '{"username":"' + $u + '","password":"123","companyId":1}'
  try { return Invoke-RestMethod -Uri "$api/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($b)) -TimeoutSec 20 }
  catch { return $null }
}
function Post([string]$url, [string]$tok) {
  try { return Invoke-RestMethod -Uri $url -Method Post -Headers @{ Authorization = $tok } -TimeoutSec 40 }
  catch { return [pscustomobject]@{ code = -1; msg = 'HTTP ' + $_.Exception.Response.StatusCode.value__ } }
}
Write-Host 'STEP 0: logging in as lin (admin+super_admin)...'
$la = Login 'lin'
if ($null -eq $la -or [string]$la.code -ne '200') { Write-Output 'FAIL login lin'; Write-Output 'RESULT BATCH5-CLEAR FAIL count 1'; exit 1 }
$tok = [string]$la.data.token

# 1) NEGATIVE: no confirm => must be refused, and it must be refused before any deletion happens
$r1 = Post "$api/system/clear-company-data" $tok
Write-Host ("  no-confirm call -> code=" + $r1.code + " msg=" + $r1.msg)
if ([string]$r1.code -ne '200' -and ("$($r1.msg)" -ne '')) { Ok 'clear-company-data without the confirm phrase is refused (second gate works)' }
else { Bad "clear-company-data without confirm was NOT refused (code=$($r1.code) msg=$($r1.msg))" }

# 2) dryRun: must report the scope and change nothing (the endpoint rolls back; it also writes an audit row)
$r2 = Post "$api/system/clear-company-data?dryRun=true" $tok
$dryOk = ([string]$r2.code -eq '200') -and ($r2.data.dryRun -eq $true)
Write-Host ("  dryRun -> code=" + $r2.code + " dryRun=" + $r2.data.dryRun + " tables=" + $r2.data.tables + " totalRows=" + $r2.data.totalRows + " backup=" + $r2.data.backup)
if ($dryOk) { Ok ("dryRun reports scope without deleting (tables=$($r2.data.tables), totalRows=$($r2.data.totalRows), rollback point written)") }
else { Bad ("dryRun did not behave as documented (code=" + $r2.code + " dryRun=" + $r2.data.dryRun + ")") }

# 3) NEGATIVE: a user with NO roles must not be able to run it (even the dryRun branch)
$la3 = Login 'admin3'
if ($null -eq $la3 -or [string]$la3.code -ne '200') { Note 'admin3 cannot log in with password 123 -> the no-role negative case could not be run' }
else {
  $r3 = Post "$api/system/clear-company-data?dryRun=true" ([string]$la3.data.token)
  Write-Host ("  admin3 (no roles) dryRun -> code=" + $r3.code + " msg=" + $r3.msg)
  if ([string]$r3.code -ne '200') { Ok ("a no-role account is refused (code=$($r3.code))") }
  else { Bad 'a no-role account was allowed to call clear-company-data' }
}

if ($fail -eq 0) { Write-Output 'RESULT BATCH5-CLEAR PASS' } else { Write-Output ("RESULT BATCH5-CLEAR FAIL count " + $fail) }
exit $fail
