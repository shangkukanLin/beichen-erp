# verify-fix-f7-141.ps1
#
# F7-141 regression (2026-09-20) for the three "housekeeping" items the user asked for together:
#   B2  test hygiene  - the regression scripts restored business tables but NOT warehouse_stock_log
#       (an append-only ledger), so every run left its audit rows behind (210 rows accumulated on
#       2026-09-20 alone). Three scripts now snapshot MAX(id) and delete only the rows THEY added.
#   B3① tenant isolation for attachments - uploads used to land in {dateDir}/{name} with no tenant
#       segment, so any logged-in user holding a URL could download another company's attachment.
#       Now: company/{companyId}/{dateDir}/{name}, and the download route verifies the segment
#       against the caller's company (legacy URLs keep working through the old route).
#   B3② orphan files  - deleting a drawing removed only the DB row, leaving the physical file
#       forever. DrawingController.delete now also removes the file (idempotent).
#   B4  dead column   - verified rather than changed: sale_exchange has NO total_amount column
#       any more, so the DROP recommended in §39-F7-118 is already satisfied (no DDL needed).
#
# Needs a UTF-8 BOM (Chinese expectations).

$ErrorActionPreference = 'Continue'
$B = 'http://localhost:8080/api'
$MYSQL = 'E:\dev\mysql\mysql-8.0.46-winx64\bin\mysql.exe'
$DB = 'beichen_erp'
$RUNDIR = $PSScriptRoot
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
# raw HTTP status of a download (the endpoint returns a file, not the R envelope)
function HttpStatus([string]$path, [string]$tok) {
  try {
    $req = [System.Net.HttpWebRequest]::Create($B + $path)
    $req.Method = 'GET'
    if ($tok) { $req.Headers.Add('Authorization', $tok) }
    $resp = $req.GetResponse()
    $code = [int]$resp.StatusCode
    $resp.Close()
    return $code
  } catch [System.Net.WebException] {
    try { return [int]$_.Exception.Response.StatusCode } catch { return -1 }
  } catch { return -1 }
}

$TAG = 'VERIFY-F7141'
Write-Output '=== 0) login ==='
$admin = Login 'lin' '123'
if ($null -eq $admin) { Bad 'cannot login as admin'; Write-Output 'RESULT FAIL count=1'; exit 1 }
Ok 'admin login ok'

# ---------- 1) B3-① tenant isolation on the download route ----------
Write-Output ''
Write-Output '=== 1) B3-① attachment download must be scoped to the caller company ==='
$stOther = HttpStatus "/dev/file/download/2/20260920/probe-$TAG.txt" $admin
if ($stOther -eq 403) { Ok 'a path carrying ANOTHER company id is refused with 403 (tenant check runs on the real route)' }
elseif ($stOther -eq 404) { Bad 'cross-company download returned 404 instead of 403 => the company check did NOT run (it fell through to "file missing")' }
else { Bad ("cross-company download returned unexpected status $stOther") }
$stSame = HttpStatus "/dev/file/download/1/20260920/probe-$TAG.txt" $admin
if ($stSame -eq 404) { Ok 'the same path with the CORRECT company id reaches the file lookup (404 = file absent, i.e. not blocked)' }
else { Bad ("same-company download returned $stSame (expected 404 for a missing file)") }

# ---------- 2) B3-② upload -> download -> delete drawing -> physical file gone ----------
Write-Output ''
Write-Output '=== 2) B3-② upload, download, then delete the drawing and verify the physical file is gone ==='
$tmp = Join-Path $env:TEMP "$TAG.txt"
Set-Content -Path $tmp -Value "hello-$TAG" -Encoding UTF8
$uploaded = $null
try {
  $wc = New-Object System.Net.WebClient
  $wc.Headers.Add('Authorization', $admin)
  $bytes = $wc.UploadFile("$B/dev/file/upload", 'POST', $tmp)
  $txt = [Text.Encoding]::UTF8.GetString($bytes)
  $j = $txt | ConvertFrom-Json
  if ($j.code -eq 200) { $uploaded = [string]$j.data }
} catch {
  Bad ('upload failed: ' + $_.Exception.Message)
}
if ($null -eq $uploaded) { Skip 'cannot upload (see above); skipping the orphan-file part' }
else {
  Info "uploaded url = $uploaded"
  if ($uploaded -match '/download/1/') { Ok 'the returned URL carries the caller company segment (/download/1/...)' }
  else { Bad ("the returned URL has no company segment: $uploaded") }
  $rel = $uploaded -replace '^/api', ''
  $stDl = HttpStatus $rel $admin
  if ($stDl -eq 200) { Ok 'the freshly uploaded file downloads with 200' } else { Bad ("download of the fresh file returned $stDl") }

  $projId = [int](SqlOne "SELECT id FROM dev_project WHERE company_id=1 ORDER BY id LIMIT 1")
  $dwBase = [int](SqlOne 'SELECT COUNT(*) FROM dev_drawing')
  if ($projId -lt 1) { Skip 'no project to attach the drawing to' }
  else {
    $rc = Req 'POST' ("/dev/project/$projId/drawing") $admin (@{ docName = $TAG; docType = 'OTHER'; fileUrl = $uploaded; remark = $TAG })
    if ((BCode $rc) -ne 200) { Bad ('creating the drawing failed: ' + (BMsg $rc)) }
    else {
      $did = [int]$rc.data.id
      Ok ("drawing row created (id=$did) pointing at the uploaded file")
      $rd = Req 'DELETE' ("/dev/project/$projId/drawing/$did") $admin $null
      if ((BCode $rd) -eq 200) { Ok 'drawing deleted' } else { Bad ('deleting the drawing failed: ' + (BMsg $rd)) }
      $stAfter = HttpStatus $rel $admin
      if ($stAfter -eq 404) { Ok 'the PHYSICAL file is gone after the drawing was deleted (404) => no orphan left' }
      else { Bad ("the physical file is still downloadable after deletion (status=$stAfter) => orphan file remains") }
    }
    $dwNow = [int](SqlOne 'SELECT COUNT(*) FROM dev_drawing')
    if ($dwNow -eq $dwBase) { Ok ("dev_drawing back to baseline ($dwNow)") } else { Bad ("dev_drawing=$dwNow (was $dwBase)") }
  }
}
Remove-Item $tmp -ErrorAction SilentlyContinue

# ---------- 3) B2 stock-log hygiene: the three patched scripts must not grow warehouse_stock_log ----------
Write-Output ''
Write-Output '=== 3) B2 the patched regression scripts no longer accumulate stock-log rows ==='
$slBefore = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
Info "warehouse_stock_log MAX(id) before = $slBefore"
$scripts = @('verify-f7-80-close-reopen.ps1', 'verify-fix-f7-61-69-71-79.ps1', 'verify-fix-f7-64-74.ps1')
foreach ($s in $scripts) {
  $p = Join-Path $RUNDIR $s
  if (-not (Test-Path $p)) { Skip "missing script $s"; continue }
  $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $p 2>$null | Select-String -Pattern '^RESULT'
  $line = if ($out) { $out[0].Line } else { 'NO RESULT LINE' }
  if ($line -like 'RESULT PASS*') { Ok ("$s -> $line") } else { Bad ("$s -> $line") }
  $mid = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
  if ($mid -le $slBefore) { Ok ("  after $s : MAX(id)=$mid (<= $slBefore, no accumulation)") }
  else { Bad ("  after $s : MAX(id)=$mid > $slBefore => this run left stock-log rows behind") }
}
$slAfter = [int](SqlOne 'SELECT IFNULL(MAX(id),0) FROM warehouse_stock_log')
if ($slAfter -eq $slBefore) { Ok ("warehouse_stock_log MAX(id) unchanged across all three runs ($slBefore)") }
else { Bad ("warehouse_stock_log MAX(id) moved $slBefore -> $slAfter") }

# ---------- 4) B4: the dead column really is gone ----------
Write-Output ''
Write-Output '=== 4) B4 the dead column sale_exchange.total_amount is absent (DROP already satisfied) ==='
$col = SqlOne "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='$DB' AND TABLE_NAME='sale_exchange' AND COLUMN_NAME='total_amount'"
if ([int]$col -eq 0) { Ok 'sale_exchange.total_amount does not exist => §39-F7-118''s DROP recommendation is already satisfied (no DDL needed)' }
else { Bad 'sale_exchange.total_amount still exists => a DROP COLUMN is still required' }

Write-Output ''
if ($script:fail -eq 0) { Write-Output ("RESULT PASS (skip=" + $script:skip + ")"); exit 0 }
Write-Output ("RESULT FAIL count=" + $script:fail + " skip=" + $script:skip)
exit 1
